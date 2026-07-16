CREATE UNIQUE INDEX uq_session_queue_entry_id ON session.session(queue_entry_id);

CREATE UNIQUE INDEX uq_session_active_student
    ON session.session(student_id)
    WHERE status IN ('WAITING_START', 'IN_PROGRESS');

CREATE UNIQUE INDEX uq_queue_entry_patient_waiting
    ON queue.queue_entry(patient_id)
    WHERE status = 'WAITING';

ALTER TABLE queue.queue_entry ADD COLUMN session_id UUID;
ALTER TABLE queue.queue_entry ADD COLUMN jitsi_link VARCHAR(300);
