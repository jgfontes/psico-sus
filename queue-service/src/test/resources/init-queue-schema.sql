CREATE SCHEMA IF NOT EXISTS queue;

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

CREATE INDEX idx_queue_entry_status ON queue.queue_entry(status);
CREATE INDEX idx_queue_entry_patient_id ON queue.queue_entry(patient_id);
CREATE UNIQUE INDEX uq_queue_entry_patient_waiting ON queue.queue_entry(patient_id) WHERE status = 'WAITING';
