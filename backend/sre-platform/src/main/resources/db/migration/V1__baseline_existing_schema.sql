-- Baseline of the existing Hibernate-managed schema, before agent session management.
-- Existing installations without Flyway history baseline at version 1.
-- Idempotent DDL also supports the empty Flyway history left by an installation with no migrations.
create table if not exists agent_action (created_at timestamp(6) not null, finished_at timestamp(6), started_at timestamp(6), id uuid not null, session_id uuid not null, risk_level varchar(255) not null check ((risk_level in ('SAFE','LOW','MEDIUM','HIGH'))), status varchar(255) not null check ((status in ('PENDING','RUNNING','SUCCESS','FAILED','REJECTED'))), target_id varchar(255), target_type varchar(255) not null check ((target_type in ('SERVICE','SERVER','CONTAINER','PROCESS'))), type varchar(255) not null check ((type in ('READ_METRICS','READ_LOGS','HEALTH_CHECK','GET_STATUS','RESTART_PROCESS','RESTART_CONTAINER'))), parameters jsonb, result jsonb, primary key (id));
create table if not exists agent_session (attempt integer not null check (attempt >= 1), created_at timestamp(6) not null, finished_at timestamp(6), started_at timestamp(6) not null, id uuid not null, incident_id uuid not null, status varchar(255) not null check (status in ('RUNNING','COMPLETED','FAILED','CANCELLED')), summary text, primary key (id));
create table if not exists alert (created_at timestamp(6) not null, started_at timestamp(6) not null, id uuid not null, incident_id uuid not null, integration_id uuid not null, service_id uuid not null, external_id varchar(255) not null, message text not null, severity varchar(255) not null check ((severity in ('LOW','MEDIUM','HIGH','CRITICAL'))), type varchar(255) not null check ((type in ('HIGH_ERROR_RATE','HIGH_LATENCY','SERVICE_DOWN','CUSTOM'))), raw_payload jsonb not null, primary key (id));
create table if not exists approval (created_at timestamp(6) not null, decided_at timestamp(6), action_id uuid not null, id uuid not null, user_id uuid not null, comment text, status varchar(255) not null check ((status in ('PENDING','APPROVED','REJECTED'))), primary key (id));
create table if not exists hypothesis (confidence float(53) not null, rank integer not null check ((rank>=1)), created_at timestamp(6) not null, id uuid not null, session_id uuid not null, description text not null, title varchar(255) not null, primary key (id));
create table if not exists incident (created_at timestamp(6) not null, resolved_at timestamp(6), started_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, project_id uuid not null, service_id uuid not null, description text not null, root_cause_summary text, severity varchar(255) not null check ((severity in ('LOW','MEDIUM','HIGH','CRITICAL'))), status varchar(255) not null check ((status in ('CREATED','INVESTIGATING','WAITING_APPROVAL','EXECUTING_ACTION','VERIFYING','RESOLVED','ESCALATED'))), title varchar(255) not null, primary key (id));
create table if not exists integration (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, project_id uuid not null, server_id uuid not null, base_url varchar(255) not null, name varchar(255) not null, status varchar(255) not null check ((status in ('CONNECTED','DISCONNECTED','ERROR'))), type varchar(255) not null check ((type in ('PROMETHEUS','LOKI','WEBHOOK'))), config jsonb not null, primary key (id), constraint uk_integration_server_type unique (server_id, type));
create table if not exists policy (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, project_id uuid not null, action_type varchar(255) not null check ((action_type in ('READ_METRICS','READ_LOGS','HEALTH_CHECK','GET_STATUS','RESTART_PROCESS','RESTART_CONTAINER'))), description text, permission varchar(255) not null check ((permission in ('AUTO','REQUIRE_APPROVAL','FORBIDDEN'))), conditions jsonb, primary key (id));
create table if not exists project (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, description text not null, name varchar(255) not null, primary key (id));
create table if not exists project_metrics_access (id uuid not null, project_id uuid not null, user_id uuid not null, primary key (id), unique (project_id, user_id));
create table if not exists server (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, project_id uuid not null, ip_address varchar(64) not null, os varchar(100) not null, hostname varchar(255) not null, name varchar(255) not null, status varchar(255) not null check ((status in ('ACTIVE','INACTIVE'))), primary key (id));
create table if not exists server_discovery (completed_at timestamp(6) with time zone, requested_at timestamp(6) with time zone not null, job_id uuid not null, server_id uuid not null, status varchar(16) not null check ((status in ('QUEUED','RUNNING','SUCCEEDED','FAILED'))), error_code varchar(64), error varchar(512), snapshot_json text, primary key (server_id));
create table if not exists server_ssh_connection (port integer not null, server_id uuid not null, credential_id varchar(64) not null, host_key_fingerprint varchar(64) not null, username varchar(64) not null, host varchar(255) not null, primary key (server_id));
create table if not exists service (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, project_id uuid not null, server_id uuid, description text, healthcheck_url varchar(255), name varchar(255) not null, status varchar(255) not null check ((status in ('UP','DOWN','DEGRADED','UNKNOWN'))), primary key (id));
create table if not exists users (is_active boolean not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, email varchar(255) not null unique, name varchar(255) not null, password_hash varchar(255) not null, role varchar(255) not null check ((role in ('ADMIN','ENGINEER'))), primary key (id));
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'agent_action'::regclass
                   AND conname = 'fkfdkv86j533bnfn16lwr3l3q54') THEN
        alter table if exists agent_action add constraint FKfdkv86j533bnfn16lwr3l3q54 foreign key (session_id) references agent_session;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'agent_session'::regclass
                   AND conname = 'fknkx60qjtk31y4repmyg5tuhlu') THEN
        alter table if exists agent_session add constraint FKnkx60qjtk31y4repmyg5tuhlu foreign key (incident_id) references incident;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'alert'::regclass
                   AND conname = 'fkst1mcw6bc871qxybolodo7rwx') THEN
        alter table if exists alert add constraint FKst1mcw6bc871qxybolodo7rwx foreign key (incident_id) references incident;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'alert'::regclass
                   AND conname = 'fknh9h6pb8j3xwn6v07ofesx3ej') THEN
        alter table if exists alert add constraint FKnh9h6pb8j3xwn6v07ofesx3ej foreign key (integration_id) references integration;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'alert'::regclass
                   AND conname = 'fkh1v6hdrl9pnsh4irhgh1x0tck') THEN
        alter table if exists alert add constraint FKh1v6hdrl9pnsh4irhgh1x0tck foreign key (service_id) references service;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'approval'::regclass
                   AND conname = 'fktpmf7dqh124ba5ljiubd63066') THEN
        alter table if exists approval add constraint FKtpmf7dqh124ba5ljiubd63066 foreign key (action_id) references agent_action;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'approval'::regclass
                   AND conname = 'fki7of4jfxwle1fgdb4dqgfdkwn') THEN
        alter table if exists approval add constraint FKi7of4jfxwle1fgdb4dqgfdkwn foreign key (user_id) references users;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'hypothesis'::regclass
                   AND conname = 'fk4kfkgcv3dkhqinpvi25bgnlrx') THEN
        alter table if exists hypothesis add constraint FK4kfkgcv3dkhqinpvi25bgnlrx foreign key (session_id) references agent_session;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'incident'::regclass
                   AND conname = 'fk9iyx0bx22qbvtxaybkh34s32m') THEN
        alter table if exists incident add constraint FK9iyx0bx22qbvtxaybkh34s32m foreign key (service_id) references service;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'incident'::regclass
                   AND conname = 'fk3561lcvcftw3yhvr6brnb8beb') THEN
        alter table if exists incident add constraint FK3561lcvcftw3yhvr6brnb8beb foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'integration'::regclass
                   AND conname = 'fkcn2mlqii30qgx07cqeeg521ut') THEN
        alter table if exists integration add constraint FKcn2mlqii30qgx07cqeeg521ut foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'integration'::regclass
                   AND conname = 'fk7t5anlwm0lmuye831iorfpqox') THEN
        alter table if exists integration add constraint FK7t5anlwm0lmuye831iorfpqox foreign key (server_id) references server;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'policy'::regclass
                   AND conname = 'fk7ddsycap2t5bfmfigom20g8nl') THEN
        alter table if exists policy add constraint FK7ddsycap2t5bfmfigom20g8nl foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'project_metrics_access'::regclass
                   AND conname = 'fk8jxl6237cor4q7s1h3jnfajof') THEN
        alter table if exists project_metrics_access add constraint FK8jxl6237cor4q7s1h3jnfajof foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'project_metrics_access'::regclass
                   AND conname = 'fko16e0wqan9aewqh1bya9wo4qk') THEN
        alter table if exists project_metrics_access add constraint FKo16e0wqan9aewqh1bya9wo4qk foreign key (user_id) references users;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'server'::regclass
                   AND conname = 'fkc2q2chwi3c5gqpckpsts7nppi') THEN
        alter table if exists server add constraint FKc2q2chwi3c5gqpckpsts7nppi foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'server_discovery'::regclass
                   AND conname = 'fktg9k1la1d35shnkgksj9jql9m') THEN
        alter table if exists server_discovery add constraint FKtg9k1la1d35shnkgksj9jql9m foreign key (server_id) references server;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'server_ssh_connection'::regclass
                   AND conname = 'fkqiv1gvmi6bw9h86a15sox2fcx') THEN
        alter table if exists server_ssh_connection add constraint FKqiv1gvmi6bw9h86a15sox2fcx foreign key (server_id) references server;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'service'::regclass
                   AND conname = 'fknwpqsts1xrsew7slgo1ewror7') THEN
        alter table if exists service add constraint FKnwpqsts1xrsew7slgo1ewror7 foreign key (project_id) references project;
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'service'::regclass
                   AND conname = 'fkd13jsmlav5x3d9f9jyua68cp3') THEN
        alter table if exists service add constraint FKd13jsmlav5x3d9f9jyua68cp3 foreign key (server_id) references server;
    END IF;
END $$;
