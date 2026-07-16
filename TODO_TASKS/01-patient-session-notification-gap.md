# Problem 01 — The patient is never told a session/room is ready

**Status:** ✅ Resolved
**Area:** `session-service`, `availability-service`, (missing) patient-facing delivery channel
**Severity:** High — it breaks the core happy path. A session is created but the patient can never reach the video room.

---

## TL;DR

When a session is created, a Jitsi meeting link is generated and stored, but **there is no code path that delivers that link (or even the `sessionId`) back to the patient.** The patient obtains a token, joins the queue, gets matched, a room is created — and then the flow dead-ends on the patient side. The patient has no way to discover that a room exists, what its `sessionId` is, or what the Jitsi URL is.

This is currently masked in diagrams/spec by a box labelled "Notify patient with link", but **that notification step is not implemented and there is no `notification-service` in this repository.**

---

## Why this happens — exact code trace

1. **The room and link are created and persisted.**
   `SessionService.start(...)` generates the room and saves the `Session` row, including `jitsiLink` and `jitsiRoomName`:
   - `session-service/src/main/java/com/psicosus/session/service/SessionService.java:51-86`
   - `session-service/src/main/java/com/psicosus/session/service/JitsiService.java:24-28`

2. **The link is returned in the HTTP response — but the only caller throws that body away.**
   `SessionService.start(...)` returns a `StartSessionResponse` containing `jitsiLink` (`SessionService.java:83-85`). Its **only** caller is `availability-service`, invoked from the RabbitMQ listener, and that client explicitly discards the response body:
   ```java
   // availability-service/src/main/java/com/psicosus/availability/client/SessionServiceClient.java:23-31
   restClient.post()
       .uri("/session/start")
       .header("Authorization", "Bearer " + internalTokenService.issue())
       .contentType(MediaType.APPLICATION_JSON)
       .body(new StartSessionRequest(patientId, queueEntryId))
       .retrieve()
       .toBodilessEntity();   // <-- response (with jitsiLink) is dropped here
   ```
   The trigger is `QueueEventListener.onPatientJoinedQueue(...)` at
   `availability-service/src/main/java/com/psicosus/availability/service/QueueEventListener.java:31-42`,
   which runs in a RabbitMQ consumer thread — there is no HTTP client waiting on it and no patient connection to push to.

3. **The link IS published in an event — but no patient-facing consumer exists.**
   `SessionService.start(...)` publishes `SessionStartedEvent` (which includes `jitsiLink`) to the exchange with routing key `session.started` (`SessionService.java:79-81`). Per the messaging table in `PSICOSUS_SPEC.md` §6, `session.started` is consumed **only by `supervision-service`**. Nothing on that event fans out toward the patient.

4. **The patient has no identity to be notified through.**
   The patient token is minted with a freshly generated random UUID and no persisted record (`auth-service/src/main/java/com/psicosus/auth/controller/AuthController.java:70-76`). There is:
   - no patient contact detail (email/phone) stored anywhere,
   - no device/registration token,
   - no websocket / SSE / long-poll channel,
   - no `notification-service` (the 6 services in this repo are auth, queue, availability, session, supervision, medical-record).

5. **Polling doesn't help either — the poll endpoint never returns the link.**
   A patient could poll `GET /queue/position/{patientId}`, but `QueuePositionResponse` only carries `position`, `estimatedWaitMinutes`, and `status` — never a `sessionId` or `jitsiLink`
   (`queue-service/.../dto/QueuePositionResponse.java`, `QueueService.position(...)` at `QueueService.java:65-73`).
   And `GET /session/{sessionId}` requires knowing `sessionId` up front, which the patient never receives — so it can't be used to bootstrap discovery.

**Net result:** the link exists in the `session` DB row and nowhere the patient can see it.

---

## Concrete failure scenario

1. Patient: `POST /auth/patient-session` → gets `patientToken` + `patientId`.
2. Patient: `POST /queue/join` → `WAITING`.
3. A student is `AVAILABLE`; `availability-service` calls `POST /session/start`; a `Session` row is created with a valid `jitsiLink`.
4. Patient polls `GET /queue/position/{patientId}` → still returns `status: WAITING` (see Problem 03 note: the queue entry is never even moved out of `WAITING`), and **no link**.
5. Patient is stuck. There is no API response, event, or channel that ever hands them the room URL.

---

## What a fix needs to decide (out of scope to solve here, but the design must cover)

- **Delivery mechanism** for an anonymous, identity-less patient. Candidates: (a) patient polls a patient-facing endpoint that returns their active session + link once matched; (b) a real push channel (SSE/websocket) keyed by `patientId`; (c) a `notification-service` with a real contact detail collected at `queue/join`.
- **Where the patient reads it from.** The lowest-friction option is likely extending the queue/session read model so `GET /queue/position/{patientId}` (or a new `GET /session/active/patient/{patientId}`) returns `sessionId` + `jitsiLink` once a session exists for that patient. That keeps the patient's existing poll loop and needs no new infra.
- **Who owns the write.** If polling on the queue side, something must copy `sessionId`/link (or at least a "your session is ready" pointer) back into a place the patient can query. Today nothing writes back toward the queue/patient at all (see Problem 03 and the related queue-entry lifecycle note).

## Related

- **Problem 03** — the queue entry is never transitioned to `IN_PROGRESS`/`ATTENDED`, so even the patient's `status` field stays `WAITING` after a match. Any polling-based notification fix will likely also want to fix that transition.
- The queue-entry lifecycle gap (entries never leave `WAITING` except via cancel/expire) is closely tied to this and is worth tackling together.
