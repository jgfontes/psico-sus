# Problem 04 — Queue entries never transition to `IN_PROGRESS` / `ATTENDED`

**Status:** ✅ Resolved
**Area:** `queue-service` (missing event consumer), `session-service` (no callback toward queue)
**Severity:** High — a successfully attended patient still looks like they're waiting, forever, until the timeout job wrongly marks them `EXPIRED`.

---

## TL;DR

`QueueStatus` declares five states — `WAITING`, `IN_PROGRESS`, `ATTENDED`, `CANCELLED`, `EXPIRED` — but **`IN_PROGRESS` and `ATTENDED` are never written anywhere in the codebase.** A `queue_entry` only ever moves `WAITING → CANCELLED` (patient leaves) or `WAITING → EXPIRED` (timeout job). When a patient is matched and their session starts, and even after that session ends, their queue entry stays `WAITING`. The queue's model of "who is still waiting" is therefore permanently wrong.

---

## Why this happens — exact code trace

The enum has the states:
```java
// queue-service/src/main/java/com/psicosus/queue/entity/QueueStatus.java
public enum QueueStatus { WAITING, IN_PROGRESS, ATTENDED, CANCELLED, EXPIRED }
```

But nothing sets the two "progress/success" ones. The complete set of writers to `queue_entry.status` is:

- `QueueService.join(...)` → sets `WAITING` (`queue-service/.../service/QueueService.java:45-63`)
- `QueueService.leave(...)` → sets `CANCELLED` (`QueueService.java:75-83`)
- `QueueService.expireStaleEntries(...)` → sets `EXPIRED` (`QueueService.java:95-105`)

That's it. Confirmed structurally:

- **`queue-service` has no `@RabbitListener` at all** — it only *publishes* `patient.joined.queue`; it consumes nothing. So `session.started` / `session.ended` never reach it.
- **`session-service` never calls back to `queue-service`.** `SessionService.start(...)` receives `queueEntryId` and stores it on the `Session` row (`session-service/.../service/SessionService.java:51-86`), but it never tells queue-service "this entry is now in progress." Likewise `end(...)` (`SessionService.java:99-125`) publishes `session.ended` but nothing on the queue side listens.

The spec explicitly expected these transitions — `PSICOSUS_SPEC.md` §5.4 says session-start should update the queue entry, and session-end "Updates queue entry status to `ATTENDED`" — **neither was implemented.**

---

## Concrete failure scenarios

**Scenario A — attended patient wrongly counted, then wrongly expired**
1. Patient joins → `WAITING`.
2. Matched, session created, session ends normally.
3. The `queue_entry` is *still* `WAITING`. It keeps being counted by `countByStatus(WAITING)`, so it inflates `position` for every later patient (`QueueService.position(...)` at `QueueService.java:65-73`) and `size()` (`QueueService.java:86-88`).
4. 30 minutes after they *joined*, `expireStaleEntries()` marks the already-attended patient `EXPIRED` — a factually wrong terminal state (they were attended, not abandoned).

**Scenario B — corrupts sibling TODOs**
- Any duplicate-join guard scoped to `WAITING` (Problem 03) will misbehave, because a matched patient is still `WAITING` and would either be blocked from legitimately re-queuing later, or counted as a live duplicate.
- Any notification-by-polling fix (Problem 01) wants to flip the patient's visible `status` to something like `IN_PROGRESS` when the room is ready — impossible while the transition doesn't exist.

---

## What a fix needs to do

Close the lifecycle so an entry follows `WAITING → IN_PROGRESS → ATTENDED` on the happy path:

- **`WAITING → IN_PROGRESS`** when a session is created for that `queueEntryId`.
- **`IN_PROGRESS → ATTENDED`** when the session ends.

Design decisions:

- **Mechanism.** Preferred: `queue-service` consumes the existing events. `session.started` already carries the data and is published today (`SessionStartedEvent`, `SessionService.java:79-81`) — **but it does not currently include `queueEntryId`**, so either add `queueEntryId` to that event, or add a dedicated event / internal callback. `session.ended` (`SessionEndedEvent`) also lacks `queueEntryId` and would need it (or a `patientId`-based lookup) to locate the entry.
- **Alternative.** A direct internal REST callback `session-service → queue-service` (`PATCH /queue/{queueEntryId}/status`) mirrors how session-service already calls availability-service. Simpler causality, but adds synchronous coupling and a new secured endpoint.
- **Exclude terminal/in-progress rows from matching + counts.** Once `IN_PROGRESS`/`ATTENDED` are real, update `position`/`size` and the timeout sweep so they only consider genuinely `WAITING` rows, and so `expireStaleEntries()` can never expire an entry that already reached a session.
- **Idempotency.** The status transition must be safe against event redelivery (see Problem 05) — a redelivered `session.started` should not corrupt an already-`ATTENDED` entry. Prefer conditional transitions (only advance from the expected prior state).

## Related

- **Problem 01** (notification) and **Problem 03** (duplicate-join) both depend on a correct `WAITING`/`IN_PROGRESS` distinction — fix this first or alongside them.
- **Problem 05** (idempotency/redelivery) governs how the new consumer must behave under duplicate messages.
