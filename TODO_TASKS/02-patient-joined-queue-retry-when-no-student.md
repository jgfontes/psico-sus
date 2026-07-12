# Problem 02 — `patient.joined.queue` is dropped when no student is available

**Status:** Open — no retry/reprocessing exists yet
**Area:** `availability-service` (`QueueEventListener`), RabbitMQ topology, `queue-service` (timeout job)
**Severity:** High — a patient who joins while all students are busy is effectively abandoned until the 30-minute expiry job marks them `EXPIRED`.

---

## TL;DR

When `availability-service` consumes a `patient.joined.queue` event and **no student is `AVAILABLE` at that instant**, the event is acknowledged and discarded. Nothing re-evaluates that patient later when a student frees up. The only thing that ever happens to the waiting patient is the `queue-service` scheduler eventually marking the entry `EXPIRED`.

We need a deliberate strategy to **reprocess a patient** once a student becomes available (or after a delay), instead of silently dropping the event.

---

## Why this happens — exact code trace

```java
// availability-service/src/main/java/com/psicosus/availability/service/QueueEventListener.java:31-42
@RabbitListener(queues = RabbitMQConfig.QUEUE_PATIENT_JOINED)
public void onPatientJoinedQueue(PatientJoinedQueueEvent event) {
    boolean anyAvailable = statusRepository.findLatestStatusPerStudent().stream()
            .anyMatch(s -> s.getStatus() == StudentStatusValue.AVAILABLE);

    if (!anyAvailable) {
        log.info("No student available for queueEntryId={}; patient stays WAITING", event.queueEntryId());
        return;   // <-- event is ACKed and gone. No re-queue, no retry, no record kept.
    }

    sessionServiceClient.startSession(event.patientId(), event.queueEntryId());
}
```

Key facts:

- The listener returns normally, so Spring AMQP **acks** the message and RabbitMQ deletes it. There is no `throw`, no `nack`, no requeue.
- When a student later flips to `AVAILABLE` via `StudentService.updateStatus(...)`
  (`availability-service/src/main/java/com/psicosus/availability/service/StudentService.java:66-84`),
  that method **only writes a new `student_status` row. It never looks at the queue** and never triggers matching for anyone who is already waiting. So "student becomes free" is a dead-end for waiting patients.
- The `session.ended` consumer (`QueueEventListener.onSessionEnded`, `QueueEventListener.java:44-47`) marks a student available again via `markAvailableAndAddHours(...)` — but again, **no waiting patient is re-matched** as a result.
- The only backstop is `QueueService.expireStaleEntries()`
  (`queue-service/src/main/java/com/psicosus/queue/service/QueueService.java:95-105`), a `@Scheduled(fixedDelay = 60_000)` job that flips `WAITING` entries older than the timeout to `EXPIRED`. That is a timeout, not a retry.

**Net result:** matching is edge-triggered on patient-arrival only. It is never triggered on student-availability, so a patient who arrives at the wrong moment waits until they expire.

---

## Concrete failure scenario

1. All students are `IN_SESSION`.
2. Patient A joins → `patient.joined.queue` published → consumed → `anyAvailable == false` → event dropped, A stays `WAITING`.
3. 2 minutes later Student S finishes; `session.ended` → S becomes `AVAILABLE`.
4. **Nothing happens.** No code re-examines patient A. S sits idle; A sits waiting.
5. 30 minutes after step 2, `expireStaleEntries()` marks A `EXPIRED`. A never got a session despite a student having been free for ~28 minutes.

---

## Design considerations any solution must respect

- **Idempotency / no double-booking.** Whatever retry we build must not create two sessions for the same `queueEntryId`, and must not hand the same student to two patients. Note the current matching is *already* racy: `StudentService.next()` (`StudentService.java:86-113`) and the subsequent `markStudentInSession(...)` are not atomic, so concurrent patients can select the same student. A retry loop will amplify this unless we add a guard (e.g. conditional status transition / row lock / unique constraint on an active session per student).
- **Ordering / fairness.** Waiting patients should ideally be matched oldest-first (FIFO). `next()` already picks the oldest-available student; the reprocessing side should pick the oldest-waiting patient.
- **Terminal states.** A retry must skip entries that are no longer `WAITING` (already `CANCELLED`/`EXPIRED`/matched).
- **Poison messages.** If `session/start` keeps failing (e.g. supervisor CRP unresolvable — `StudentService.next()` can throw 409/502), infinite requeue would hot-loop. Any requeue approach needs a max-attempt / dead-letter bound.

---

## Solution plan — 3 candidate approaches

### Approach A — Student-availability triggers a match (event-driven pull)

When a student becomes `AVAILABLE` (in `StudentService.updateStatus(...)` and in the `session.ended` handler's `markAvailableAndAddHours(...)`), actively pull the oldest `WAITING` patient and start a session for them.

- **How:** availability-service needs to know who is waiting. Either (a) query queue-service via a new internal `GET /queue/next-waiting` endpoint, or (b) publish a `student.became.available` event that a matcher consumes.
- **Pros:**
  - Directly fixes the root cause (matching is now triggered on *both* patient-arrival and student-availability).
  - Latency is minimal — patient is matched the moment a student frees up.
  - No busy-polling; purely event/edge driven.
- **Cons / tradeoffs:**
  - Adds a queue-service → availability-service (or vice-versa) coupling/endpoint that doesn't exist today.
  - Two trigger paths (patient-arrival and student-availability) both mutate matching → higher chance of the double-booking race; needs a real concurrency guard.
  - "Oldest waiting patient" needs to be queryable from queue-service, which currently exposes only per-patient position, not a global next-in-line.

### Approach B — RabbitMQ delayed retry / requeue with backoff (message-driven retry)

Instead of dropping the event when no student is free, re-deliver it later with a delay, until it succeeds or a max-attempt cap sends it to a dead-letter queue.

- **How:** on `anyAvailable == false`, republish the event to a delay queue (RabbitMQ TTL + Dead-Letter-Exchange pattern, or the delayed-message plugin), carrying an incremented `attempt` header. A DLQ captures patients who exhaust retries (feed them the CVV-188 guidance, same as expiry).
- **Pros:**
  - Small, localized change — stays inside availability-service + broker topology; no new cross-service HTTP endpoint.
  - Naturally bounded via max attempts + DLQ; poison messages are contained.
  - Keeps the "reprocess the event" mental model the team already has.
- **Cons / tradeoffs:**
  - Polling-in-disguise: retries fire on a timer, not the instant a student frees up, so there's avoidable wait latency (tunable via delay interval — shorter delay = faster match but more broker churn).
  - Must reconcile the retry attempt against the live queue entry state — if the patient already `CANCELLED`/`EXPIRED`/got matched, the retry must no-op (needs a queue-service status check to stay correct).
  - Requires broker features (DLX/TTL or delayed-exchange plugin) and careful topology; more moving parts in infra.

### Approach C — Periodic reconciliation sweep (scheduled matcher)

A scheduled job periodically scans for `(WAITING patient, AVAILABLE student)` pairs and starts sessions for as many as it can match.

- **How:** a `@Scheduled` matcher (in availability-service, or a new small matcher component) runs every N seconds: fetch waiting patients (needs a queue-service read endpoint), fetch available students (already available locally), match oldest-to-oldest until one side is exhausted. The existing `expireStaleEntries()` job in queue-service is a precedent for this pattern.
- **Pros:**
  - Self-healing and simple to reason about — even if an event is lost/dropped, the next sweep recovers it. Robust against missed events and transient failures.
  - Central place to enforce FIFO fairness and a single matching path (fewer concurrent writers → easier to make the race-safe guard correct).
  - No broker plugin needed.
- **Cons / tradeoffs:**
  - Latency bounded by the sweep interval (e.g. up to N seconds to match even when a student is free right now).
  - Still needs a way to list waiting patients from queue-service (same new endpoint as A/C share).
  - A periodic scan is wasteful when idle; needs sensible interval tuning and a lock so two instances don't double-run.

### Recommendation seed (not final)

A pragmatic combination is often **C as the safety net + A for low-latency happy path**: student-availability triggers an immediate match attempt (fast), while a slow reconciliation sweep guarantees nobody is stranded if an event/trigger is missed. Whatever we pick, **the concurrency guard against double-booking a student is mandatory** and should be designed first, because all three approaches expose it.

## Related

- The double-booking race in `StudentService.next()` + `markStudentInSession()` is a prerequisite concern for any of these — see the note in Problem 03 and the queue-entry lifecycle gap.
- Problem 01 (notification) — once a retry actually matches a waiting patient, they still need to be *told*. These should be designed together.
