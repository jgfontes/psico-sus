# Problem 03 — `POST /queue/join` lets the same patient join the queue multiple times

**Status:** Open — no duplicate check exists yet
**Area:** `queue-service` (`QueueController`, `QueueService`, `QueueEntryRepository`)
**Severity:** Medium — corrupts queue position/size counts and can spawn multiple sessions for one patient.

---

## TL;DR

`QueueService.join(...)` unconditionally inserts a **new** `queue_entry` row every time it is called. There is no check for an existing active (`WAITING`) entry for the same patient. A patient (or a retrying client / double-tap) can create N duplicate `WAITING` entries, each of which inflates the queue size, distorts everyone's position estimate, and can independently trigger a `patient.joined.queue` event → potentially N sessions for one patient.

We need a guard on `queue/join` that rejects (or is idempotent about) a join when the patient already has an active entry.

---

## Why this happens — exact code trace

```java
// queue-service/src/main/java/com/psicosus/queue/service/QueueService.java:45-63
@Transactional
public QueueJoinResponse join(UUID patientId, QueueJoinRequest request) {
    long waitingAhead = repository.countByStatus(QueueStatus.WAITING);
    int position = (int) waitingAhead + 1;
    int estimatedWait = position * MINUTES_PER_PATIENT_AHEAD;

    QueueEntry entry = QueueEntry.builder()
            .patientId(patientId)
            .status(QueueStatus.WAITING)
            .position(position)
            .estimatedWaitMin(estimatedWait)
            .build();
    entry = repository.save(entry);   // <-- always inserts; no "already in queue?" check

    rabbitTemplate.convertAndSend(exchangeName, ROUTING_KEY_PATIENT_JOINED,
            new PatientJoinedQueueEvent(entry.getId(), patientId, request.patientName(), request.symptomsDescription()));

    return new QueueJoinResponse(entry.getId(), position, estimatedWait, entry.getStatus().name());
}
```

- The controller (`queue-service/src/main/java/com/psicosus/queue/controller/QueueController.java:30-36`) reads `patientId` from the JWT subject and calls `join(...)` with no pre-check.
- There is **no** DB uniqueness constraint or `existsBy...` guard preventing multiple active entries per patient.
- The repository already has a suitable lookup we can reuse:
  ```java
  // queue-service/src/main/java/com/psicosus/queue/repository/QueueEntryRepository.java:18
  Optional<QueueEntry> findFirstByPatientIdAndStatusOrderByCreatedAtDesc(UUID patientId, QueueStatus status);
  ```

### Downstream damage caused by duplicates

- `position` = `countByStatus(WAITING) + 1` and `size()` both count every `WAITING` row (`QueueService.java:47, 86-88`), so duplicates inflate the numbers for **all** patients, not just the offender.
- Each duplicate publishes its own `patient.joined.queue` event → `availability-service` may start a **separate session per duplicate** for the same patient (`QueueEventListener.java:31-42`).

---

## Concrete failure scenario

1. Patient taps "Join" twice (or the client retries on a slow network).
2. Two `WAITING` rows are created for the same `patientId`; two `patient.joined.queue` events fire.
3. If two students are available, two sessions get created for one patient; if one is available, the patient still shows up twice in everyone's position math.

---

## Proposed check (what to implement)

Before inserting, reject or short-circuit when the patient already has an **active** entry. "Active" = `WAITING` (and arguably `IN_PROGRESS` once that transition exists — see the lifecycle note below).

Sketch:

```java
// in QueueService.join(...), before building the new entry:
repository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, QueueStatus.WAITING)
        .ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "patient already has an active queue entry");
        });
```

Design decisions to settle:

- **409 Conflict vs. idempotent 200.** Returning `409` is simplest and explicit. Alternatively, make join idempotent: if an active entry exists, return **that** entry's `QueueJoinResponse` instead of erroring (nicer for double-tap/retry clients). Pick one and document it.
- **Race safety.** A check-then-insert is still racy under two concurrent joins. To be correct under concurrency, back the guard with a **DB constraint**, e.g. a partial unique index on `(patient_id)` where `status = 'WAITING'` (Postgres partial unique index), and translate the resulting `DataIntegrityViolationException` into `409`. The application-level check gives the friendly path; the constraint guarantees correctness. (Note: `queue-service` currently has no `@ExceptionHandler` for `DataIntegrityViolationException`, so that translation must be added too.)
- **Which statuses count as "active."** At minimum `WAITING`. Revisit once the queue-entry lifecycle is fixed so that a matched patient is `IN_PROGRESS` rather than still `WAITING`.

---

## Related lifecycle note (worth fixing alongside this)

The enum `QueueStatus` defines `IN_PROGRESS` and `ATTENDED`
(`queue-service/src/main/java/com/psicosus/queue/entity/QueueStatus.java`), but **nothing in the codebase ever sets them** — confirmed: `queue-service` has no `@RabbitListener` and no session callback, so a queue entry only ever moves `WAITING → CANCELLED` (leave) or `WAITING → EXPIRED` (timeout). Consequences relevant to this task:

- A patient who *was* successfully matched still has a `WAITING` row, so this duplicate-check (if scoped to `WAITING`) would wrongly block them from ever re-queuing, **and** their stale `WAITING` row keeps inflating position/size until the 30-min job marks it `EXPIRED`.
- A proper fix likely needs `queue-service` to consume `session.started` (→ set entry `IN_PROGRESS`) and `session.ended` (→ set `ATTENDED`). That closes the lifecycle and makes "is the patient already active?" well-defined.

This lifecycle gap is arguably its own TODO — see "Problems not yet filed" below.
