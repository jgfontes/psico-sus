# Problem 05 — Student double-booking race & non-idempotent event handling

**Status:** Open — no concurrency guard, no idempotency key
**Area:** `availability-service` (`QueueEventListener`, `StudentService`), `session-service` (`SessionService.start`, `SessionRepository`)
**Severity:** High — the same student can be handed to two patients, and a single queued patient can spawn duplicate sessions.

This file covers two tightly-coupled concurrency defects. They share a root cause (matching has no atomic guard and no dedup key) and any fix must address both together.

---

## Part A — Double-booking race: one student, two patients

### Why it happens — exact code trace

Matching a patient to a student is done as **two separate, non-atomic steps** with no lock between them:

1. `session-service` asks availability-service for the next free student:
   ```java
   // session-service/src/main/java/com/psicosus/session/service/SessionService.java:52-71
   AvailabilityServiceClient.NextStudent nextStudent =
           availabilityServiceClient.fetchNextAvailableStudent();   // GET /availability/student/next
   ...
   session = sessionRepository.save(session);
   availabilityServiceClient.markStudentInSession(nextStudent.studentId()); // PATCH .../status IN_SESSION
   ```
2. `StudentService.next()` just reads the current latest status per student and returns the oldest `AVAILABLE` one — it takes **no lock and makes no write**:
   ```java
   // availability-service/src/main/java/com/psicosus/availability/service/StudentService.java:86-113
   StudentStatus available = latest.stream()
           .filter(s -> s.getStatus() == StudentStatusValue.AVAILABLE)
           .min(Comparator.comparing(StudentStatus::getUpdatedAt))
           .orElseThrow(...);
   ```
3. Marking the student `IN_SESSION` is a *later*, *separate* call, and it works by appending a new `student_status` row (`StudentService.updateStatus(...)` / status history model, `StudentService.java:66-84`). There is no compare-and-set ("only take this student if still AVAILABLE").

There is also **no uniqueness guard on the session side**: `SessionRepository` exposes only `findByStatus(...)` (`session-service/.../repository/SessionRepository.java`), and the `session` table/entity has no unique constraint tying a student to at most one active session (`session-service/.../entity/Session.java`).

### Failure scenario

1. Exactly one student S is `AVAILABLE`.
2. Two `patient.joined.queue` events (patients A and B) are processed concurrently — plausible today because they can arrive close together, and *very* likely once Problem 02's retry/reprocessing exists.
3. Both matcher flows call `GET /availability/student/next` before either has written `IN_SESSION`. **Both read S as available.**
4. Two sessions are created, both assigned student S. S is double-booked; one patient is effectively mismatched. `student_status` ends with two `IN_SESSION` rows.

---

## Part B — Non-idempotent event handling: one patient, duplicate sessions

### Why it happens — exact code trace

The consumer has no dedup and no idempotency key:
```java
// availability-service/src/main/java/com/psicosus/availability/service/QueueEventListener.java:31-42
@RabbitListener(queues = RabbitMQConfig.QUEUE_PATIENT_JOINED)
public void onPatientJoinedQueue(PatientJoinedQueueEvent event) {
    boolean anyAvailable = ...;
    if (!anyAvailable) { ...; return; }
    sessionServiceClient.startSession(event.patientId(), event.queueEntryId());
}
```

- `session-service`'s `start(...)` generates a **fresh random `sessionId` every call** (`SessionService.java:55`) and has no guard like "a session already exists for this `queueEntryId`." So calling it twice with the same `queueEntryId` inserts **two** distinct sessions.
- The RabbitMQ queues are durable and use Spring AMQP's default AUTO ack (`availability-service/.../config/RabbitMQConfig.java` — `new Queue(name, true)`, no manual ack). With default behavior, **a listener that throws causes the message to be requeued and redelivered.** Redelivery is normal in RabbitMQ (consumer restart, connection drop, ack timeout, an exception thrown *after* `startSession` already succeeded, etc.).

### Failure scenario

1. Patient A's `patient.joined.queue` is delivered; `startSession(...)` succeeds and a session is created.
2. Before the message is acked, the consumer connection blips (or a later line in the handler throws). RabbitMQ **redelivers** the same event.
3. The handler runs again, calls `startSession(...)` again → a **second session for the same `queueEntryId`**, consuming a second student. One patient, two rooms, two students burned.

(This is distinct from Problem 03, which is about the *patient* calling `join` twice at the HTTP layer. Part B is the broker redelivering one legitimate join.)

---

## Why they're filed together

Both are the same missing invariant expressed at different layers:
- **"A student is assigned to at most one active session."** (fixes Part A)
- **"A queue entry produces at most one session."** (fixes Part B)

A correct solution enforces both as **atomic, database-backed guarantees**, not just application-level checks — because check-then-act under concurrency is exactly what's broken here.

---

## Solution directions & tradeoffs

### For Part B — idempotency key on session creation
- **Unique constraint on `session.queue_entry_id`** + translate the resulting `DataIntegrityViolationException` into a no-op / "session already exists" response. Make `start(...)` first `findByQueueEntryId(...)` and return the existing session if present.
  - *Pros:* DB-guaranteed exactly-once per queue entry; robust against redelivery and Problem 03 duplicates alike; small change.
  - *Cons:* need a new repo finder + a `session.queue_entry_id` unique index (currently none); must decide the "already exists" response contract for the internal caller.
- **Broker-side:** move failing messages to a Dead-Letter Queue instead of infinite requeue (pairs naturally with Problem 02's retry design), and/or set manual acks so partial failures don't silently redeliver.
  - *Pros:* bounds poison-message loops. *Cons:* doesn't by itself prevent a duplicate that occurs *before* the failure; must be combined with the idempotency key.

### For Part A — atomic student claim
- **Conditional/compare-and-set claim:** turn "pick next available" + "mark IN_SESSION" into one atomic operation that only succeeds if the student is *still* `AVAILABLE` (e.g. a single guarded `UPDATE ... WHERE status = 'AVAILABLE'` returning affected-rows, or `SELECT ... FOR UPDATE SKIP LOCKED` over available students).
  - *Pros:* eliminates the read-read-both-win window; `SKIP LOCKED` also gives clean concurrent matching (each matcher grabs a distinct student). *Cons:* the current append-only `student_status` history model doesn't lock naturally — likely needs a claimable "current status" row or a dedicated assignment table.
- **Single-writer matcher:** funnel all matching through one component/path (see Problem 02, Approach C) so concurrent claims can't happen in the first place.
  - *Pros:* simplest correctness story. *Cons:* throughput bottleneck; still wants a DB guard as backstop.
- **Unique active-session-per-student constraint** as a safety net: a partial unique index on `session(student_id)` where `status IN ('WAITING_START','IN_PROGRESS')`, so even if two flows race, the second insert fails and can retry with a different student.
  - *Pros:* last-line guarantee independent of app logic. *Cons:* turns the race into an error the matcher must catch and re-match around.

### Recommendation seed (not final)
Add **both** DB-level guards first — unique `session.queue_entry_id` (Part B) and unique active-session-per-student (Part A) — because they make the invariants true regardless of how the racy application code is later refactored. Then layer the friendlier application-level checks (`findByQueueEntryId`, atomic claim) on top for good UX and fewer error paths. Design this **before** Problem 02's retry lands, since retry multiplies the concurrency these guards must survive.

## Related
- **Problem 02** — retry/reprocessing will dramatically increase concurrent matching; these guards are its prerequisite.
- **Problem 03** — HTTP-layer duplicate join; complementary (different entry point, same "one session per patient" goal).
- **Problem 04** — the queue-entry lifecycle consumer must also be made idempotent under redelivery, using the same principles.
