-- Incremental schema for an existing SRE database that already contains server.
-- Development currently uses Hibernate ddl-auto=update; it creates these tables automatically.
-- For ddl-auto=validate, apply this SQL through your schema management process before deployment.
BEGIN;
CREATE TABLE IF NOT EXISTS server_ssh_connection (
    server_id uuid PRIMARY KEY REFERENCES server(id),
    host varchar(255) NOT NULL,
    port integer NOT NULL CHECK (port BETWEEN 1 AND 65535),
    username varchar(64) NOT NULL,
    credential_id varchar(64) NOT NULL,
    host_key_fingerprint varchar(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS server_discovery (
    server_id uuid PRIMARY KEY REFERENCES server(id),
    job_id uuid NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    error_code varchar(64),
    error varchar(512),
    snapshot_json text
);
COMMIT;
