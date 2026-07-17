# Diagramas de Arquitetura — PsicoSUS

---

## 1. Arquitetura Geral

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTES                                            │
│    🧑 Paciente      🎓 Aluno      👨‍⚕️ Supervisor      🏫 Universidade            │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │ HTTPS
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY (:8090)                                      │
│                    Spring Cloud Gateway + JWT HS256                              │
│              Roteamento · Validação de Token · CORS                              │
└──┬──────────┬──────────┬──────────┬──────────┬──────────┬───────────────────────┘
   │          │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼          ▼
┌──────┐ ┌──────┐ ┌──────────┐ ┌──────┐ ┌──────────┐ ┌────────────┐
│ Auth │ │Queue │ │Availab.  │ │Sess. │ │Supervis. │ │Med. Record │
│:8080 │ │:8081 │ │:8082     │ │:8083 │ │:8084     │ │:8085       │
│      │ │      │ │          │ │      │ │          │ │            │
│ JWT  │ │ Fila │ │ Alunos   │ │Jitsi │ │Interven. │ │Prontuário  │
│Login │ │Posição│ │Matching  │ │Video │ │Monitora  │ │Horas       │
│Regis.│ │Expire│ │Claim     │ │Links │ │Sessions  │ │Validação   │
└──┬───┘ └──┬───┘ └────┬─────┘ └──┬───┘ └────┬─────┘ └─────┬──────┘
   │         │          │          │          │              │
   └─────────┴──────────┴─────┬────┴──────────┴──────────────┘
                               │
                               ▼
              ┌────────────────────────────────┐
              │        PostgreSQL 15            │
              │   6 schemas isolados por svc    │
              │  (auth, queue, availability,    │
              │   session, supervision,         │
              │   medical_record)               │
              └────────────────────────────────┘

              ┌────────────────────────────────┐
              │         RabbitMQ 3              │
              │   Topic Exchange:              │
              │   psicosus.events              │
              │                                │
              │ ┌─────────────────────────┐    │
              │ │ patient.joined.queue     │    │
              │ │ session.started          │    │
              │ │ session.ended            │    │
              │ │ supervisor.intervened     │    │
              │ └─────────────────────────┘    │
              └────────────────────────────────┘
```

---

## 2. Fluxo de Eventos (RabbitMQ)

```
 PUBLISHERS                    EXCHANGE                      CONSUMERS
 ──────────                    ────────                      ─────────

                          ┌─────────────────┐
 Queue Service ──────────►│                 │──────► Availability Service
   patient.joined.queue   │                 │          (tenta matching)
                          │                 │
 Session Service ────────►│  psicosus.events│──────► Queue Service
   session.started        │  (Topic)        │          (entry→IN_PROGRESS)
                          │                 │
 Session Service ────────►│                 │──────► Supervision Service
   session.ended          │                 │          (monitora sessões)
                          │                 │
 Supervision Service ────►│                 │──────► Queue Service
   supervisor.intervened  │                 │          (entry→ATTENDED)
                          │                 │
                          │                 │──────► Availability Service
                          │                 │          (student→AVAILABLE)
                          │                 │
                          │                 │──────► Medical Record Service
                          │                 │          (prontuário + horas)
                          │                 │
                          │                 │──────► Session Service
                          └─────────────────┘          (log intervenção)
```

---

## 3. Fluxo de Atendimento (Sequência)

```
 PACIENTE          GATEWAY        AUTH       QUEUE      RABBITMQ    AVAILABILITY   SESSION     MED.RECORD
    │                 │             │          │            │            │            │            │
    │ POST /auth/     │             │          │            │            │            │            │
    │ patient-session │             │          │            │            │            │            │
    │────────────────►│────────────►│          │            │            │            │            │
    │                 │◄────────────│          │            │            │            │            │
    │◄────────────────│ token+id    │          │            │            │            │            │
    │                 │             │          │            │            │            │            │
    │ POST /queue/join│             │          │            │            │            │            │
    │────────────────►│────────────────────────►            │            │            │            │
    │                 │             │          │──publish──►│            │            │            │
    │◄────────────────│◄───────────────────────│            │            │            │            │
    │ pos=1, WAITING  │             │          │            │            │            │            │
    │                 │             │          │            │──consume──►│            │            │
    │                 │             │          │            │            │            │            │
    │                 │             │          │            │  anyAvail? │            │            │
    │                 │             │          │            │     YES    │            │            │
    │                 │             │          │            │            │            │            │
    │                 │             │          │            │────────POST /session/start──────────►│
    │                 │             │          │            │            │◄──claim────│            │
    │                 │             │          │            │            │───student──►│            │
    │                 │             │          │            │            │            │            │
    │                 │             │          │            │◄─publish───│            │            │
    │                 │             │          │◄──consume──│session.    │            │            │
    │                 │             │          │            │started     │            │            │
    │                 │             │          │            │            │            │            │
    │                 │             │          │ IN_PROGRESS│            │            │            │
    │                 │             │          │ +jitsiLink │            │            │            │
    │                 │             │          │            │            │            │            │
    │ GET /queue/     │             │          │            │            │            │            │
    │ position/{id}   │             │          │            │            │            │            │
    │────────────────►│────────────────────────►            │            │            │            │
    │◄────────────────│◄───────────────────────│            │            │            │            │
    │ IN_PROGRESS     │             │          │            │            │            │            │
    │ jitsiLink ✓     │             │          │            │            │            │            │
    │                 │             │          │            │            │            │            │
    │ ═══════ PACIENTE ABRE O LINK JITSI E ENTRA NA SALA ═══════       │            │            │
    │                 │             │          │            │            │            │            │
    │ PATCH /session/ │             │          │            │            │            │            │
    │ confirm-start   │             │          │            │            │            │            │
    │────────────────►│─────────────────────────────────────────────────────────────►│            │
    │◄────────────────│◄────────────────────────────────────────────────────────────│            │
    │ IN_PROGRESS ✓   │             │          │            │            │            │            │
    │                 │             │          │            │            │            │            │
    │ ═══════ SESSÃO DE ATENDIMENTO EM ANDAMENTO ═══════════            │            │            │
    │                 │             │          │            │            │            │            │
    │ POST /session/  │             │          │            │            │            │            │
    │ {id}/end        │             │          │            │            │            │            │
    │────────────────►│─────────────────────────────────────────────────────────────►│            │
    │                 │             │          │            │◄─publish───│            │            │
    │                 │             │          │◄──consume──│session.    │            │            │
    │                 │             │          │  ATTENDED  │ended       │            │            │
    │                 │             │          │            │──consume──►│            │            │
    │                 │             │          │            │  AVAILABLE │            │            │
    │                 │             │          │            │            │──consume──►│            │
    │                 │             │          │            │            │ prontuário │            │
    │                 │             │          │            │            │ + horas    │            │
    │◄────────────────│◄────────────────────────────────────────────────────────────│            │
    │ ENDED, 45min    │             │          │            │            │            │            │
    │                 │             │          │            │            │            │            │
```

---

## 4. Modelo de Dados

```
┌─────────── schema: queue ───────────┐     ┌──────── schema: session ─────────┐
│                                     │     │                                   │
│  queue_entry                        │     │  session                          │
│  ─────────────                      │     │  ────────                         │
│  id            UUID PK              │     │  id              UUID PK          │
│  patient_id    UUID                 │     │  patient_id      UUID             │
│  status        WAITING|IN_PROGRESS  │     │  student_id      UUID             │
│                |ATTENDED|CANCELLED   │     │  supervisor_id   UUID             │
│                |EXPIRED             │     │  queue_entry_id  UUID UK ──────┐  │
│  session_id    UUID FK ─────────────┼─────┼──────────────────────────────┘   │
│  jitsi_link    VARCHAR(300)         │     │  status          WAITING_START|   │
│  position      INT                  │     │                  IN_PROGRESS|ENDED│
│  created_at    TIMESTAMP            │     │  jitsi_link      VARCHAR(300)     │
│  UK: (patient_id) WHERE WAITING     │     │  duration_minutes INT             │
│                                     │     │  UK: (student_id) WHERE active    │
└─────────────────────────────────────┘     │                                   │
                                            │  session_event                    │
                                            │  ─────────────                    │
                                            │  id, session_id, type, author_id  │
                                            └───────────────────────────────────┘

┌────── schema: availability ─────────┐     ┌──────── schema: supervision ──────┐
│                                     │     │                                   │
│  university                         │     │  supervisor                       │
│  ──────────                         │     │  ──────────                       │
│  id, name, cnpj, state, city        │     │  id, name, email, cpf, crp UK     │
│                                     │     │  university_id                    │
│  student                            │     │                                   │
│  ────────                           │     │  intervention                     │
│  id, name, email UK, cpf UK         │     │  ────────────                     │
│  university_id FK                   │     │  id, session_id, supervisor_id FK │
│  supervisor_crp, semester           │     │  reason, joined_at, left_at       │
│  completed_hours, target_hours      │     │                                   │
│                                     │     └───────────────────────────────────┘
│  student_status (append-only)       │
│  ──────────────                     │     ┌──────── schema: medical_record ───┐
│  id, student_id FK                  │     │                                   │
│  status: AVAILABLE|IN_SESSION|      │     │  record                           │
│           OFFLINE|PAUSED            │     │  ───────                           │
│  updated_at                         │     │  id, patient_id, session_id        │
│                                     │     │  student_id, supervisor_id         │
└─────────────────────────────────────┘     │  clinical_summary, icd10          │
                                            │  referral, suggested_return        │
┌────────── schema: auth ─────────────┐     │                                   │
│                                     │     │  internship_hours                 │
│  user_credential                    │     │  ────────────────                 │
│  ────────────────                   │     │  id, student_id, session_id       │
│  id, name, email UK, cpf UK         │     │  duration_minutes, session_date   │
│  password_hash, role                │     │  validated, validated_by           │
│  reference_id (→ student/sup/univ)  │     │                                   │
│                                     │     └───────────────────────────────────┘
└─────────────────────────────────────┘
```

---

## 5. Containers Docker

```
docker compose ps

NAME                            PORT          IMAGE
────────────────────────────────────────────────────────────
psico-sus-api-gateway-1         8090          api-gateway
psico-sus-auth-service-1        8080          auth-service
psico-sus-queue-service-1       8081          queue-service
psico-sus-availability-service  8082          availability-service
psico-sus-session-service-1     8083          session-service
psico-sus-supervision-service   8084          supervision-service
psico-sus-medical-record-svc    8085          medical-record-service
psico-sus-postgres-1            5432          postgres:15
psico-sus-rabbitmq-1            5672/15672    rabbitmq:3-management
```

---

## 6. Decisões de Concorrência

```
┌──────────────────────────────────────────────────────────┐
│           PREVENÇÃO DE DOUBLE-BOOKING                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Thread A: patient.joined.queue ──┐                      │
│                                   ├──► SELECT ... FROM   │
│  Thread B: patient.joined.queue ──┘    student_status    │
│                                        FOR UPDATE        │
│                                        SKIP LOCKED       │
│                                                          │
│  Resultado:                                              │
│    Thread A → pega Student 1 (locked)                    │
│    Thread B → pega Student 2 (skip locked row)           │
│                                                          │
│  Safety net (DB):                                        │
│    UNIQUE INDEX uq_session_active_student                │
│    ON session(student_id)                                │
│    WHERE status IN ('WAITING_START','IN_PROGRESS')       │
│                                                          │
├──────────────────────────────────────────────────────────┤
│           PREVENÇÃO DE SESSÃO DUPLICADA                   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  SessionService.start(queueEntryId):                     │
│    1. findByQueueEntryId → se existe, retorna existente  │
│    2. Se não, cria nova sessão                           │
│                                                          │
│  Safety net (DB):                                        │
│    UNIQUE INDEX uq_session_queue_entry_id                │
│    ON session(queue_entry_id)                            │
│                                                          │
├──────────────────────────────────────────────────────────┤
│           PREVENÇÃO DE JOIN DUPLICADO                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  QueueService.join(patientId):                           │
│    1. existsByPatientIdAndStatus(WAITING) → 409          │
│                                                          │
│  Safety net (DB):                                        │
│    UNIQUE INDEX uq_queue_entry_patient_waiting           │
│    ON queue_entry(patient_id) WHERE status = 'WAITING'   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```
