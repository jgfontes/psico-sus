# PsicoSUS — Technical Implementation Specification

> This document is intended for AI models or developers implementing the first version of PsicoSUS. It contains all necessary details: stack, architecture, database schema, endpoints, and integrations.

---

## 1. Overview

**PsicoSUS** is a mental health support platform that connects **SUS patients in crisis** to **supervised psychology students**, through real-time remote video sessions.

### System Actors

| Actor | Description |
|---|---|
| `PATIENT` | SUS user seeking immediate mental health support |
| `STUDENT` | Psychology student linked to a partner university |
| `SUPERVISOR` | Licensed psychologist responsible for monitoring student sessions |
| `UNIVERSITY` | Partner institution that registers students and supervisors |

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT (local `auth-service`, OAuth2-compatible Bearer tokens) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 15+ |
| Messaging | RabbitMQ |
| Documentation | SpringDoc OpenAPI 3 (Swagger UI) |
| Containerization | Docker + Docker Compose |
| Video calls | Jitsi Meet (meet.jit.si) |
| Build | Maven |

---

> **Authentication note:** the original design assumed **gov.br** as the OAuth2/OIDC identity provider, since a real SUS integration would authenticate citizens through it. For local development and this first implementation, gov.br is replaced by a local, self-hosted `auth-service` (see §4.6 and §5.6) that issues Bearer tokens with the same shape and purpose. Every other service validates tokens identically regardless of who issued them — swapping in real gov.br later is a configuration change (issuer/keys), not a rewrite of any endpoint or business logic.

## 3. Microservices Architecture

The system is composed of **6 independent microservices**, each with its own database schema in PostgreSQL.

```
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                          │
│                  (Spring Cloud Gateway)                     │
└───┬──────┬──────────┬──────────┬──────────┬──────────┬──────┘
    │      │          │          │          │          │
 auth-   queue-  availability- session-  supervision- medical-
 svc     svc     svc           svc       svc          record-svc
    │      │          │          │          │          │
    └──────┴──────────┴──────────┴──────────┴──────────┘
                              │
                          RabbitMQ
                    (events between services)
```

`auth-service` sits behind the gateway like the others, but its own `/auth/*` endpoints must be excluded from the gateway's Bearer-token filter — that's the one place tokens don't exist yet.

### Service Communication

- **Synchronous:** REST (direct calls between services when needed)
- **Asynchronous:** RabbitMQ for events such as `patient.joined.queue`, `session.started`, `session.ended`

---

## 4. Database — Schemas and Tables

> Each microservice has its own schema in PostgreSQL.

---

### 4.1 Schema: `queue`

#### Table: `queue.queue_entry`

```sql
CREATE TABLE queue.queue_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'WAITING',
                        -- WAITING | IN_PROGRESS | ATTENDED | CANCELLED | EXPIRED
    position            INTEGER,
    estimated_wait_min  INTEGER, -- in minutes
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    expired_at          TIMESTAMP -- filled if patient leaves without being attended
);

CREATE INDEX idx_queue_entry_status     ON queue.queue_entry(status);
CREATE INDEX idx_queue_entry_patient_id ON queue.queue_entry(patient_id);
```

---

### 4.2 Schema: `availability`

#### Table: `availability.university`

```sql
CREATE TABLE availability.university (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    cnpj       VARCHAR(14) NOT NULL UNIQUE,
    state      CHAR(2) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

#### Table: `availability.student`

```sql
CREATE TABLE availability.student (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    cpf                 VARCHAR(11) NOT NULL UNIQUE,
    supervisor_crp      VARCHAR(20), -- CRP of responsible supervisor
    university_id       UUID NOT NULL REFERENCES availability.university(id),
    semester            INTEGER NOT NULL,
    completed_hours     DECIMAL(6,2) NOT NULL DEFAULT 0,
    target_hours        DECIMAL(6,2) NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);
```

#### Table: `availability.student_status`

```sql
CREATE TABLE availability.student_status (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES availability.student(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
                 -- AVAILABLE | IN_SESSION | OFFLINE | PAUSED
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_student_status_student_id ON availability.student_status(student_id);
CREATE INDEX idx_student_status_value      ON availability.student_status(status);
```

---

### 4.3 Schema: `session`

#### Table: `session.session`

```sql
CREATE TABLE session.session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       UUID NOT NULL,
    student_id       UUID NOT NULL,
    supervisor_id    UUID NOT NULL,
    queue_entry_id   UUID NOT NULL, -- reference to the queue entry
    status           VARCHAR(30) NOT NULL DEFAULT 'WAITING_START',
                     -- WAITING_START | IN_PROGRESS | ENDED | CANCELLED
    jitsi_link       VARCHAR(300) NOT NULL,
    jitsi_room_name  VARCHAR(200) NOT NULL,
    started_at       TIMESTAMP,
    ended_at         TIMESTAMP,
    duration_minutes INTEGER, -- calculated on session end
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_patient_id ON session.session(patient_id);
CREATE INDEX idx_session_student_id ON session.session(student_id);
CREATE INDEX idx_session_status     ON session.session(status);
```

#### Table: `session.session_event`

```sql
-- Log of events within a session (start, intervention, end, etc.)
CREATE TABLE session.session_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES session.session(id),
    type        VARCHAR(50) NOT NULL,
               -- STARTED | SUPERVISOR_JOINED | SUPERVISOR_LEFT | ENDED | CANCELLED
    author_id   UUID, -- who triggered the event
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
```

---

### 4.4 Schema: `supervision`

#### Table: `supervision.supervisor`

```sql
CREATE TABLE supervision.supervisor (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    cpf            VARCHAR(11) NOT NULL UNIQUE,
    crp            VARCHAR(20) NOT NULL UNIQUE,
    university_id  UUID NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
```

#### Table: `supervision.intervention`

```sql
-- Records when a supervisor intervened in a session
CREATE TABLE supervision.intervention (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     UUID NOT NULL,
    supervisor_id  UUID NOT NULL REFERENCES supervision.supervisor(id),
    reason         TEXT,
    joined_at      TIMESTAMP NOT NULL DEFAULT now(),
    left_at        TIMESTAMP
);
```

---

### 4.5 Schema: `medical_record`

#### Table: `medical_record.record`

```sql
CREATE TABLE medical_record.record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       UUID NOT NULL,
    session_id       UUID NOT NULL,
    student_id       UUID NOT NULL,
    supervisor_id    UUID NOT NULL,
    clinical_summary TEXT NOT NULL,
    icd10            VARCHAR(10), -- ICD-10 code if applicable (e.g. F32 - Depression)
    referral         TEXT,        -- referral guidance if needed
    suggested_return DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_medical_record_patient_id ON medical_record.record(patient_id);
```

#### Table: `medical_record.internship_hours`

```sql
-- Internship hours automatically logged when a session ends
CREATE TABLE medical_record.internship_hours (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL,
    session_id       UUID NOT NULL,
    duration_minutes INTEGER NOT NULL,
    session_date     DATE NOT NULL,
    validated        BOOLEAN NOT NULL DEFAULT false,
    validated_by     UUID, -- supervisor_id
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
```

---

### 4.6 Schema: `auth`

> Backs the local `auth-service` that replaces gov.br for this implementation (see the authentication note in §2).

#### Table: `auth.user_credential`

```sql
CREATE TABLE auth.user_credential (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    cpf           VARCHAR(11) UNIQUE,   -- null for anonymous patient sessions
    password_hash VARCHAR(200),         -- bcrypt hash; null for anonymous patient sessions
    role          VARCHAR(20) NOT NULL,
                  -- PATIENT | STUDENT | SUPERVISOR | UNIVERSITY
    reference_id  UUID,                 -- links to student/supervisor/university id; null for patient
    active        BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_credential_email ON auth.user_credential(email);
CREATE INDEX idx_user_credential_cpf   ON auth.user_credential(cpf);
CREATE INDEX idx_user_credential_role  ON auth.user_credential(role);
```

---

## 5. REST Endpoints by Microservice

> All endpoints require Bearer Token authentication except `auth-service`'s `/auth/register`, `/auth/login`, and `/auth/patient-session` (see §5.1). The token contract — `Authorization: Bearer <jwt>`, with `patientId`/`studentId`/`supervisorId` read from claims, never from the request body — is identical to what a gov.br-issued token would look like; only the issuer changes between local and production.

---

### 5.1 `auth-service` — port 8080

> Replaces gov.br for local/dev environments. Issues and signs JWTs consumed as Bearer tokens by every other service. Runs entirely on your machine — no external call is made.

#### `POST /auth/register`
Creates login credentials for a `STUDENT`, `SUPERVISOR`, or `UNIVERSITY` user. Called right after the corresponding entity is created in `availability-service` / `supervision-service`.

**Request body:**
```json
{
  "name": "string",
  "email": "string",
  "cpf": "string",
  "password": "string",
  "role": "STUDENT",
  "referenceId": "uuid"
}
```

**Response 201:**
```json
{
  "userId": "uuid",
  "role": "STUDENT"
}
```

---

#### `POST /auth/login`
Authenticates with email + password and returns a signed Bearer token.

**Request body:**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "role": "STUDENT"
}
```

---

#### `POST /auth/patient-session`
Issues a short-lived Bearer token for a patient in crisis — no password step, since friction here works against the platform's purpose of immediate support. The token only grants patient-scoped actions (`queue/*`, own session, own medical record).

**Request body:**
```json
{
  "patientName": "string"
}
```

**Response 201:**
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "patientId": "uuid"
}
```

Note: patients get the same `expiresIn` as any other role — there is no separate patient-specific TTL, so `jwt.expiration-seconds` (`JWT_EXPIRATION_SECONDS`) is the only expiration setting `auth-service` has.

---

**Token claims (all tokens):**
```json
{
  "sub": "uuid (userId, or a freshly generated id for patient-session)",
  "role": "PATIENT | STUDENT | SUPERVISOR | UNIVERSITY",
  "referenceId": "uuid or null",
  "iat": 0,
  "exp": 0
}
```

Tokens are signed with **HS256** using a shared secret (`JWT_SECRET` env var) known to every microservice. Each resource server validates them with Spring Security's `NimbusJwtDecoder.withSecretKey(...)` — no JWKS endpoint needed for this local setup. Moving to gov.br later means switching to `NimbusJwtDecoder.withJwkSetUri(...)` pointed at gov.br's real JWKS endpoint and RS256 verification; no other service's code changes.

---

### 5.2 `queue-service` — port 8081

#### `POST /queue/join`
Patient joins the support queue.

**Request body:**
```json
{
  "patientId": "uuid",
  "patientName": "string",
  "symptomsDescription": "string"
}
```

**Response 201:**
```json
{
  "queueEntryId": "uuid",
  "position": 3,
  "estimatedWaitMinutes": 12,
  "status": "WAITING"
}
```

---

#### `GET /queue/position/{patientId}`
Check patient's current position in the queue.

**Response 200:**
```json
{
  "position": 2,
  "estimatedWaitMinutes": 8,
  "status": "WAITING"
}
```

---

#### `DELETE /queue/leave/{patientId}`
Patient leaves the queue.

**Response 204:** no body.

---

#### `GET /queue/size`
Returns how many patients are currently waiting (used internally by other services).

**Response 200:**
```json
{
  "total": 5
}
```

---

### 5.3 `availability-service` — port 8082

#### `POST /availability/student`
Registers a new student (performed by the university).

**Request body:**
```json
{
  "name": "string",
  "email": "string",
  "cpf": "string",
  "universityId": "uuid",
  "semester": 8,
  "targetHours": 300.0,
  "supervisorCrp": "string"
}
```

**Response 201:**
```json
{
  "studentId": "uuid",
  "name": "string",
  "status": "OFFLINE"
}
```

---

#### `PATCH /availability/student/{studentId}/status`
Student updates their availability status.

**Request body:**
```json
{
  "status": "AVAILABLE"
}
```

**Response 200:**
```json
{
  "studentId": "uuid",
  "status": "AVAILABLE",
  "updatedAt": "2026-06-16T10:00:00"
}
```

---

#### `GET /availability/student/next`
Returns the next available student (used internally by `session-service`).

**Response 200:**
```json
{
  "studentId": "uuid",
  "name": "string",
  "supervisorId": "uuid"
}
```

**Response 404:** no student currently available.

---

#### `GET /availability/student/{studentId}/hours`
Returns accumulated internship hours for a student.

**Response 200:**
```json
{
  "completedHours": 45.5,
  "targetHours": 300.0,
  "completionPercent": 15.2
}
```

---

#### `POST /availability/university`
Registers a partner university.

**Request body:**
```json
{
  "name": "string",
  "cnpj": "string",
  "state": "SP",
  "city": "string"
}
```

**Response 201:**
```json
{
  "universityId": "uuid",
  "name": "string"
}
```

---

### 5.4 `session-service` — port 8083

#### `POST /session/start`
Starts a session linking patient + student + supervisor. Generates the Jitsi link.

**Request body:**
```json
{
  "patientId": "uuid",
  "queueEntryId": "uuid"
}
```

**Internal logic:**
1. Calls `GET /availability/student/next` to get the next available student
2. Updates student status to `IN_SESSION`
3. Generates Jitsi room name: `psicosus-{sessionId}`
4. Builds link: `https://meet.jit.si/psicosus-{sessionId}`
5. Persists the session in the database
6. Publishes `session.started` event to RabbitMQ
7. Returns link to both patient and student via `notification-service`

**Response 201:**
```json
{
  "sessionId": "uuid",
  "jitsiLink": "https://meet.jit.si/psicosus-{sessionId}",
  "roomName": "psicosus-{sessionId}",
  "patientId": "uuid",
  "studentId": "uuid",
  "supervisorId": "uuid",
  "status": "WAITING_START",
  "createdAt": "2026-06-16T10:00:00"
}
```

---

#### `PATCH /session/{sessionId}/confirm-start`
Confirms that the session has actually begun (called when the student enters the room).

**Response 200:**
```json
{
  "sessionId": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "2026-06-16T10:02:00"
}
```

---

#### `POST /session/{sessionId}/end`
Ends the session. Calculates duration and triggers event to `medical-record-service`.

**Request body:**
```json
{
  "endedBy": "uuid",
  "clinicalSummary": "string",
  "icd10": "F32",
  "referral": "string",
  "suggestedReturn": "2026-07-01"
}
```

**Internal logic:**
1. Calculates `duration_minutes` = `ended_at` - `started_at`
2. Updates student status to `AVAILABLE`
3. Updates queue entry status to `ATTENDED`
4. Publishes `session.ended` event with full payload to RabbitMQ
5. `medical-record-service` consumes the event and persists the record

**Response 200:**
```json
{
  "sessionId": "uuid",
  "status": "ENDED",
  "durationMinutes": 45,
  "endedAt": "2026-06-16T10:47:00"
}
```

---

#### `GET /session/{sessionId}`
Returns details of a session.

**Response 200:**
```json
{
  "sessionId": "uuid",
  "jitsiLink": "https://meet.jit.si/psicosus-{sessionId}",
  "patientId": "uuid",
  "studentId": "uuid",
  "supervisorId": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "2026-06-16T10:02:00"
}
```

---

#### `GET /session/active`
Lists all sessions currently in progress (used by `supervision-service`).

**Response 200:**
```json
[
  {
    "sessionId": "uuid",
    "studentName": "string",
    "patientId": "uuid",
    "jitsiLink": "string",
    "startedAt": "2026-06-16T10:02:00",
    "currentDurationMinutes": 12
  }
]
```

---

### 5.5 `supervision-service` — port 8084

#### `GET /supervision/active-sessions`
Lists all active sessions for students under the authenticated supervisor's responsibility.

**Response 200:**
```json
[
  {
    "sessionId": "uuid",
    "studentId": "uuid",
    "studentName": "string",
    "jitsiLink": "string",
    "startedAt": "2026-06-16T10:02:00",
    "currentDurationMinutes": 15
  }
]
```

---

#### `POST /supervision/intervene/{sessionId}`
Supervisor registers an intervention and receives the session link.

**Request body:**
```json
{
  "supervisorId": "uuid",
  "reason": "string"
}
```

**Response 200:**
```json
{
  "interventionId": "uuid",
  "jitsiLink": "https://meet.jit.si/psicosus-{sessionId}",
  "sessionId": "uuid",
  "joinedAt": "2026-06-16T10:20:00"
}
```

---

#### `PATCH /supervision/intervention/{interventionId}/leave`
Supervisor registers leaving the session.

**Response 200:**
```json
{
  "interventionId": "uuid",
  "leftAt": "2026-06-16T10:35:00"
}
```

---

#### `POST /supervision/supervisor`
Registers a supervisor linked to a university.

**Request body:**
```json
{
  "name": "string",
  "email": "string",
  "cpf": "string",
  "crp": "string",
  "universityId": "uuid"
}
```

**Response 201:**
```json
{
  "supervisorId": "uuid",
  "name": "string",
  "crp": "string"
}
```

---

### 5.6 `medical-record-service` — port 8085

> This service consumes RabbitMQ events (`session.ended`) and also exposes REST endpoints for querying.

#### `GET /medical-record/patient/{patientId}`
Returns full care history for a patient.

**Response 200:**
```json
[
  {
    "recordId": "uuid",
    "sessionId": "uuid",
    "clinicalSummary": "string",
    "icd10": "F32",
    "referral": "string",
    "suggestedReturn": "2026-07-01",
    "createdAt": "2026-06-16T10:47:00"
  }
]
```

---

#### `GET /medical-record/internship-hours/student/{studentId}`
Returns all internship hours logged for a student.

**Response 200:**
```json
{
  "studentId": "uuid",
  "totalMinutes": 2730,
  "totalHours": 45.5,
  "records": [
    {
      "sessionId": "uuid",
      "durationMinutes": 45,
      "sessionDate": "2026-06-16",
      "validated": false
    }
  ]
}
```

---

#### `PATCH /medical-record/internship-hours/{internshipHoursId}/validate`
Supervisor validates internship hours for a session.

**Request body:**
```json
{
  "supervisorId": "uuid"
}
```

**Response 200:**
```json
{
  "internshipHoursId": "uuid",
  "validated": true,
  "validatedBy": "uuid"
}
```

---

## 6. Messaging — RabbitMQ Events

| Exchange | Routing Key | Published by | Consumed by |
|---|---|---|---|
| `psicosus.events` | `patient.joined.queue` | `queue-service` | `availability-service` |
| `psicosus.events` | `session.started` | `session-service` | `supervision-service` |
| `psicosus.events` | `session.ended` | `session-service` | `medical-record-service`, `availability-service` |
| `psicosus.events` | `supervisor.intervened` | `supervision-service` | `session-service` |

### Payload: `session.ended` event

```json
{
  "sessionId": "uuid",
  "patientId": "uuid",
  "studentId": "uuid",
  "supervisorId": "uuid",
  "durationMinutes": 45,
  "sessionDate": "2026-06-16",
  "clinicalSummary": "string",
  "icd10": "F32",
  "referral": "string",
  "suggestedReturn": "2026-07-01"
}
```

---

## 7. Jitsi Meet Integration

### How it works

Jitsi Meet is a **free and open-source** video conferencing platform. The public instance at `meet.jit.si` requires no authentication or API key to create rooms — just access a URL with a unique room name.

### Link generation in `session-service`

```java
@Service
public class JitsiService {

    private static final String JITSI_BASE_URL  = "https://meet.jit.si/";
    private static final String ROOM_PREFIX     = "psicosus-";

    /**
     * Generates a unique room name and the Jitsi access link.
     * The room name is based on the session UUID to guarantee uniqueness.
     */
    public JitsiRoomDTO generateRoom(UUID sessionId) {
        String roomName = ROOM_PREFIX + sessionId.toString();
        String link     = JITSI_BASE_URL + roomName;
        return new JitsiRoomDTO(roomName, link);
    }
}
```

```java
public record JitsiRoomDTO(String roomName, String jitsiLink) {}
```

### Room security note

By default, anyone with the link can join a room on `meet.jit.si`. For the MVP this is acceptable since the link is only sent to the patient, student, and supervisor via `notification-service`.

**Future improvement:** use **Jitsi JWT Authentication** to restrict access exclusively to authorized participants. This requires deploying a self-hosted Jitsi instance with token support.

### `application.yml` configuration

```yaml
jitsi:
  base-url: https://meet.jit.si/
  room-prefix: psicosus-
  # For future JWT auth version:
  # app-id: psicosus
  # app-secret: ${JITSI_SECRET}
```

---

## 8. Docker Compose

```yaml
version: '3.9'

services:

  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: psicosus
      POSTGRES_PASSWORD: psicosus
      POSTGRES_DB: psicosus
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672" # management panel: http://localhost:15672
    environment:
      RABBITMQ_DEFAULT_USER: psicosus
      RABBITMQ_DEFAULT_PASS: psicosus

  auth-service:
    build: ./auth-service
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
      JWT_EXPIRATION_SECONDS: 28800
    depends_on:
      - postgres

  queue-service:
    build: ./queue-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      SPRING_RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
    depends_on:
      - postgres
      - rabbitmq
      - auth-service

  availability-service:
    build: ./availability-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      SPRING_RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
      AUTH_SERVICE_URL: http://auth-service:8080
    depends_on:
      - postgres
      - rabbitmq
      - auth-service

  session-service:
    build: ./session-service
    ports:
      - "8083:8083"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      SPRING_RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
      AVAILABILITY_SERVICE_URL: http://availability-service:8082
      JITSI_BASE_URL: https://meet.jit.si/
    depends_on:
      - postgres
      - rabbitmq
      - availability-service
      - auth-service

  supervision-service:
    build: ./supervision-service
    ports:
      - "8084:8084"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      SPRING_RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
      SESSION_SERVICE_URL: http://session-service:8083
    depends_on:
      - postgres
      - rabbitmq
      - auth-service

  medical-record-service:
    build: ./medical-record-service
    ports:
      - "8085:8085"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/psicosus
      SPRING_DATASOURCE_USERNAME: psicosus
      SPRING_DATASOURCE_PASSWORD: psicosus
      SPRING_RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-me}
    depends_on:
      - postgres
      - rabbitmq
      - auth-service

volumes:
  pgdata:
```

> `JWT_SECRET` defaults to a fixed dev value so the stack runs out of the box with `docker compose up`. Override it with a `.env` file for anything beyond local use.

---

## 9. Database Initialization Script (`init.sql`)

```sql
-- Create schemas
CREATE SCHEMA IF NOT EXISTS queue;
CREATE SCHEMA IF NOT EXISTS availability;
CREATE SCHEMA IF NOT EXISTS session;
CREATE SCHEMA IF NOT EXISTS supervision;
CREATE SCHEMA IF NOT EXISTS medical_record;
CREATE SCHEMA IF NOT EXISTS auth;

-- UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================
-- SCHEMA: queue
-- =====================
CREATE TABLE queue.queue_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    position            INTEGER,
    estimated_wait_min  INTEGER,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    expired_at          TIMESTAMP
);
CREATE INDEX idx_queue_entry_status     ON queue.queue_entry(status);
CREATE INDEX idx_queue_entry_patient_id ON queue.queue_entry(patient_id);

-- =====================
-- SCHEMA: availability
-- =====================
CREATE TABLE availability.university (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    cnpj       VARCHAR(14) NOT NULL UNIQUE,
    state      CHAR(2) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE availability.student (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(150) NOT NULL,
    email            VARCHAR(150) NOT NULL UNIQUE,
    cpf              VARCHAR(11) NOT NULL UNIQUE,
    supervisor_crp   VARCHAR(20),
    university_id    UUID NOT NULL REFERENCES availability.university(id),
    semester         INTEGER NOT NULL,
    completed_hours  DECIMAL(6,2) NOT NULL DEFAULT 0,
    target_hours     DECIMAL(6,2) NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE availability.student_status (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID NOT NULL REFERENCES availability.student(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_student_status_student_id ON availability.student_status(student_id);
CREATE INDEX idx_student_status_value      ON availability.student_status(status);

-- =====================
-- SCHEMA: session
-- =====================
CREATE TABLE session.session (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       UUID NOT NULL,
    student_id       UUID NOT NULL,
    supervisor_id    UUID NOT NULL,
    queue_entry_id   UUID NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'WAITING_START',
    jitsi_link       VARCHAR(300) NOT NULL,
    jitsi_room_name  VARCHAR(200) NOT NULL,
    started_at       TIMESTAMP,
    ended_at         TIMESTAMP,
    duration_minutes INTEGER,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_patient_id ON session.session(patient_id);
CREATE INDEX idx_session_student_id ON session.session(student_id);
CREATE INDEX idx_session_status     ON session.session(status);

CREATE TABLE session.session_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES session.session(id),
    type        VARCHAR(50) NOT NULL,
    author_id   UUID,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- =====================
-- SCHEMA: supervision
-- =====================
CREATE TABLE supervision.supervisor (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    cpf            VARCHAR(11) NOT NULL UNIQUE,
    crp            VARCHAR(20) NOT NULL UNIQUE,
    university_id  UUID NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE supervision.intervention (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     UUID NOT NULL,
    supervisor_id  UUID NOT NULL REFERENCES supervision.supervisor(id),
    reason         TEXT,
    joined_at      TIMESTAMP NOT NULL DEFAULT now(),
    left_at        TIMESTAMP
);

-- =====================
-- SCHEMA: medical_record
-- =====================
CREATE TABLE medical_record.record (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       UUID NOT NULL,
    session_id       UUID NOT NULL,
    student_id       UUID NOT NULL,
    supervisor_id    UUID NOT NULL,
    clinical_summary TEXT NOT NULL,
    icd10            VARCHAR(10),
    referral         TEXT,
    suggested_return DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_medical_record_patient_id ON medical_record.record(patient_id);

CREATE TABLE medical_record.internship_hours (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL,
    session_id       UUID NOT NULL,
    duration_minutes INTEGER NOT NULL,
    session_date     DATE NOT NULL,
    validated        BOOLEAN NOT NULL DEFAULT false,
    validated_by     UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- =====================
-- SCHEMA: auth
-- =====================
CREATE TABLE auth.user_credential (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    cpf           VARCHAR(11) UNIQUE,
    password_hash VARCHAR(200),
    role          VARCHAR(20) NOT NULL,
    reference_id  UUID,
    active        BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_credential_email ON auth.user_credential(email);
CREATE INDEX idx_user_credential_cpf   ON auth.user_credential(cpf);
CREATE INDEX idx_user_credential_role  ON auth.user_credential(role);
```

---

## 10. Full Attendance Flow (Step by Step)

```
0. Patient obtains a Bearer token before touching the queue
   POST /auth/patient-session
   → auth-service issues a short-lived JWT (no password step)

1. Patient joins the queue
   POST /queue/join   (Authorization: Bearer <jwt>)
   → event: patient.joined.queue (RabbitMQ)

2. availability-service consumes the event
   → checks if any student has status AVAILABLE
   → if yes, triggers session-service to start a session

3. session-service starts the session
   POST /session/start
   → fetches available student via GET /availability/student/next
   → generates Jitsi link: https://meet.jit.si/psicosus-{sessionId}
   → persists session in the database
   → event: session.started (RabbitMQ)
   → sends link to both patient and student

4. Student confirms entering the room
   PATCH /session/{sessionId}/confirm-start
   → status changes to IN_PROGRESS

5. Supervisor monitors (optional)
   GET /supervision/active-sessions
   → can intervene via POST /supervision/intervene/{sessionId}
   → receives the same Jitsi link for the session

6. Student ends the session
   POST /session/{sessionId}/end
   → calculates duration
   → event: session.ended (RabbitMQ)

7. medical-record-service consumes session.ended
   → persists clinical record
   → logs student internship hours

8. availability-service consumes session.ended
   → updates student status to AVAILABLE
   → student returns to the available pool
```

---

## 11. Final Implementation Notes

- **Authentication:** all endpoints must validate the Bearer Token issued by the local `auth-service` (§4.6, §5.1). `patientId`, `studentId`, and `supervisorId` must be extracted from the JWT's claims, not from the request body, to prevent spoofing. Each resource server validates with a shared `JWT_SECRET` (HS256) — no external call to gov.br or anywhere else is made in this implementation.
- **Migrating to gov.br later:** this is a configuration change, not a rewrite. Replace `auth-service`'s local login with a redirect to gov.br's OAuth2/OIDC flow, and switch every resource server's decoder from `NimbusJwtDecoder.withSecretKey(...)` to `NimbusJwtDecoder.withJwkSetUri(...)` pointed at gov.br's JWKS endpoint (RS256). Endpoint contracts, request/response bodies, and claim names stay the same.
- **Empty queue handling:** if no student is available when a patient joins, the patient should receive a wait estimate and be notified as soon as a student becomes available.
- **Queue timeout:** patients waiting more than 30 minutes without being attended should have their entry marked as `EXPIRED` and receive an alternative guidance message (e.g. CVV hotline - 188).
- **Testing:** implement integration tests for the full flow using Testcontainers (PostgreSQL + RabbitMQ).
- **Swagger:** enable SpringDoc on each service with `springdoc.api-docs.enabled=true`, accessible at `/swagger-ui.html`.
