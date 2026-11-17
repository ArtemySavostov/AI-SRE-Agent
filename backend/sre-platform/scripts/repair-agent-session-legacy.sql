-- One-time repair for the mixed legacy/current schema produced by ddl-auto=update
-- with Flyway disabled. Not a substitute for V1/V2 on a pristine legacy database.
-- Preserve all legacy values and relationships. psql must use ON_ERROR_STOP=1.
BEGIN;
ALTER TABLE agent_session ALTER COLUMN attempt SET DEFAULT 1;
ALTER TABLE agent_session ALTER COLUMN incident_id DROP NOT NULL;
ALTER TABLE agent_session ALTER COLUMN started_at DROP NOT NULL;
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
COMMIT;
