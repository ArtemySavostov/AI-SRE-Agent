package ru.savostov.sre_platform.agentSession;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Uses an explicitly supplied disposable PostgreSQL database; each test owns a separate schema. */
@EnabledIfEnvironmentVariable(named = "AGENT_SESSION_TEST_DB_URL", matches = ".+")
class AgentSessionPostgresTests {
    private final String url = System.getenv("AGENT_SESSION_TEST_DB_URL");
    private final String username = System.getenv().getOrDefault("AGENT_SESSION_TEST_DB_USER", "postgres");
    private final String password = System.getenv().getOrDefault("AGENT_SESSION_TEST_DB_PASSWORD", "");

    @Test void freshDatabaseMigratesAndEnforcesConstraints() throws Exception {
        withSchema((schema, connection) -> {
            flyway(schema).load().migrate();
            fixture(connection);
            execute(connection, "insert into agent_session (id, service_id, status, created_at, updated_at) values " +
                    "('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 'PENDING', now(), now())");
            assertEquals("0", scalar(connection, "select recovery_attempts from agent_session"));
            assertThrows(SQLException.class, () -> execute(connection, "update agent_session set status = 'INVALID'"));
            assertThrows(SQLException.class, () -> execute(connection, "update agent_session set recovery_attempts = -1"));
            assertThrows(SQLException.class, () -> execute(connection, "update agent_session set service_id = '00000000-0000-0000-0000-000000000099'"));
            assertThrows(SQLException.class, () -> execute(connection, "insert into agent_event (id, agent_session_id, type, message, created_at, sequence_number) values " +
                    "(gen_random_uuid(), '00000000-0000-0000-0000-000000000099', 'SESSION_CREATED', 'test', now(), 1)"));
            assertEquals("2", scalar(connection, "select count(*) from flyway_schema_history where type = 'SQL' and success"));
        });
    }

    @Test void legacyHibernateDatabaseIsBaselinedAndDataIsPreserved() throws Exception {
        assertLegacyMigration(false);
    }

    @Test void legacyDatabaseWithEmptyFlywayHistoryIsMigratedWithoutDataLoss() throws Exception {
        assertLegacyMigration(true);
    }

    private void assertLegacyMigration(boolean emptyHistory) throws Exception {
        withSchema((schema, connection) -> {
            flyway(schema).target("1").load().migrate();
            fixture(connection);
            execute(connection, "insert into incident (id, project_id, service_id, title, description, severity, status, started_at, created_at, updated_at) values " +
                    "('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'test', 'test', 'LOW', 'CREATED', now(), now(), now())");
            execute(connection, "insert into agent_session (id, incident_id, attempt, status, started_at, created_at, summary) values " +
                    "('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', 2, 'RUNNING', now(), now(), 'legacy')");
            execute(connection, emptyHistory ? "delete from flyway_schema_history" : "drop table flyway_schema_history");
            flyway(schema).baselineOnMigrate(true).baselineVersion("1").load().migrate();
            assertEquals("00000000-0000-0000-0000-000000000002", scalar(connection, "select service_id from agent_session"));
            assertEquals("legacy", scalar(connection, "select summary from agent_session"));
            assertEquals("2", scalar(connection, "select attempt from agent_session"));
            assertEquals("0", scalar(connection, "select recovery_attempts from agent_session"));
            execute(connection, "update agent_session set status = 'WAITING_APPROVAL'");
            assertNotNull(scalar(connection, "select updated_at from agent_session"));
            flyway(schema).load().validate();
        });
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration flyway(String schema) {
        return Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema);
    }
    private void withSchema(SchemaTest test) throws Exception {
        String schema = "agent_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "create schema " + schema);
            try {
                execute(connection, "set search_path to " + schema);
                test.run(schema, connection);
            } finally { execute(connection, "drop schema " + schema + " cascade"); }
        }
    }
    private void fixture(Connection c) throws SQLException {
        execute(c, "insert into project (id, name, description, created_at, updated_at) values ('00000000-0000-0000-0000-000000000001', 'test', 'test', now(), now())");
        execute(c, "insert into service (id, project_id, name, status, created_at, updated_at) values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'test', 'UNKNOWN', now(), now())");
    }
    private static void execute(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement()) { s.execute(sql); } }
    private static String scalar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    @FunctionalInterface interface SchemaTest { void run(String schema, Connection connection) throws Exception; }
}
