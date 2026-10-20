-- Keep legacy sessions and their incident linkage; derive the new direct service linkage.
ALTER TABLE agent_session ADD COLUMN service_id uuid;
UPDATE agent_session s SET service_id = i.service_id FROM incident i WHERE s.incident_id = i.id;
ALTER TABLE agent_session ALTER COLUMN service_id SET NOT NULL;
ALTER TABLE agent_session ADD CONSTRAINT fk_agent_session_service FOREIGN KEY (service_id) REFERENCES service(id);
ALTER TABLE agent_session ALTER COLUMN incident_id DROP NOT NULL;
ALTER TABLE agent_session ALTER COLUMN started_at DROP NOT NULL;
-- Keep the historical attempt column for compatibility, but new sessions do not supply it.
ALTER TABLE agent_session ALTER COLUMN attempt SET DEFAULT 1;
ALTER TABLE agent_session ADD COLUMN recovery_attempts integer NOT NULL DEFAULT 0 CHECK (recovery_attempts >= 0);
ALTER TABLE agent_session ADD COLUMN error_message text;
ALTER TABLE agent_session ADD COLUMN updated_at timestamp(6);
UPDATE agent_session SET updated_at = COALESCE(finished_at, started_at, created_at);
ALTER TABLE agent_session ALTER COLUMN updated_at SET NOT NULL;

-- Hibernate-generated status check names vary between installations.
DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'agent_session'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) ~ '\mstatus\M'
    LOOP
        EXECUTE format('ALTER TABLE agent_session DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE agent_session ADD CONSTRAINT ck_agent_session_status
    CHECK (status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED'));
CREATE INDEX idx_agent_session_service ON agent_session(service_id, created_at DESC);
CREATE INDEX idx_agent_session_status ON agent_session(status);

CREATE TABLE agent_event (
    id uuid PRIMARY KEY,
    agent_session_id uuid NOT NULL REFERENCES agent_session(id),
    type varchar(255) NOT NULL CHECK (type IN (
        'SESSION_CREATED', 'SESSION_STARTED', 'DIAGNOSTIC_STARTED', 'METRICS_COLLECTED', 'LOGS_COLLECTED',
        'HYPOTHESIS_GENERATED', 'APPROVAL_REQUESTED', 'SESSION_COMPLETED', 'SESSION_FAILED', 'SESSION_CANCELLED')),
    message text NOT NULL,
    created_at timestamp(6) NOT NULL,
    sequence_number bigint NOT NULL CHECK (sequence_number > 0),
    CONSTRAINT uq_agent_event_session_sequence UNIQUE (agent_session_id, sequence_number)
);
-- The unique index above also covers lookups by agent_session_id.
