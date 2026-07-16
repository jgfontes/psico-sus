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
    session_id          UUID,
    jitsi_link          VARCHAR(300),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    expired_at          TIMESTAMP
);
CREATE INDEX idx_queue_entry_status     ON queue.queue_entry(status);
CREATE INDEX idx_queue_entry_patient_id ON queue.queue_entry(patient_id);
CREATE UNIQUE INDEX uq_queue_entry_patient_waiting ON queue.queue_entry(patient_id) WHERE status = 'WAITING';

-- =====================
-- SCHEMA: availability
-- =====================
CREATE TABLE availability.university (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    cnpj       VARCHAR(14) NOT NULL UNIQUE,
    state      VARCHAR(2) NOT NULL,
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
    student_name     VARCHAR(150), -- snapshot at session-start time, avoids a lookup for GET /session/active
    started_at       TIMESTAMP,
    ended_at         TIMESTAMP,
    duration_minutes INTEGER,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_patient_id ON session.session(patient_id);
CREATE INDEX idx_session_student_id ON session.session(student_id);
CREATE INDEX idx_session_status     ON session.session(status);
CREATE UNIQUE INDEX uq_session_queue_entry_id ON session.session(queue_entry_id);
CREATE UNIQUE INDEX uq_session_active_student ON session.session(student_id) WHERE status IN ('WAITING_START', 'IN_PROGRESS');

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
