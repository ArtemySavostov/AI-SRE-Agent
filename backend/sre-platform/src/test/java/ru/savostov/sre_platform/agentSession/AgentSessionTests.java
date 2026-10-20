package ru.savostov.sre_platform.agentSession;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.model.user.User;
import ru.savostov.sre_platform.repository.*;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import ru.savostov.sre_platform.service.agentSession.execution.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.*;
import static ru.savostov.sre_platform.model.agentEvent.AgentEventType.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:agent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
@AutoConfigureMockMvc
@WithMockUser(username = "agent-admin@test.example")
class AgentSessionTests {
    @Autowired AgentSessionService service;
    @Autowired AgentSessionExecution execution;
    @Autowired ProjectRepository projects;
    @Autowired ManagedServiceRepository services;
    @Autowired UserRepository users;
    @Autowired AgentSessionRepository sessions;
    @Autowired AgentEventRepository events;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean AgentSessionRunner runner;
    UUID serviceId;
    UUID projectId;

    @BeforeEach void setup() {
        if (users.findByEmail("agent-admin@test.example").isEmpty()) createUser("agent-admin@test.example", User.UserRole.ADMIN);
        if (users.findByEmail("engineer@test.example").isEmpty()) createUser("engineer@test.example", User.UserRole.ENGINEER);
        Project p = new Project(); p.setName("Session test"); p.setDescription("test");
        projectId = projects.save(p).getId();
        ManagedService s = new ManagedService(); s.setProject(p); s.setName("test service");
        serviceId = services.save(s).getId();
    }
    private void createUser(String email, User.UserRole role) {
        User u = new User(); u.setEmail(email); u.setName("test"); u.setPasswordHash("unused"); u.setRole(role); users.save(u);
    }
    @Test void createsPendingAndHistoryAtomically() {
        var s = service.create(serviceId);
        assertEquals(PENDING, s.status()); assertEquals(0, s.recoveryAttempts());
        assertNull(s.startedAt()); assertNull(s.finishedAt()); assertNotNull(s.createdAt()); assertNotNull(s.updatedAt());
        assertEquals(SESSION_CREATED, service.events(s.id()).getFirst().type());
        assertEquals(s.id(), service.history(serviceId).getFirst().id());
    }
    @Test void missingResourcesReturn404() throws Exception {
        mvc.perform(post("/api/services/{id}/agent-sessions", UUID.randomUUID()).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/agent-sessions/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test void startsOnceAndReturnsConflictForRepeat() throws Exception {
        UUID id = service.create(serviceId).id();
        mvc.perform(post("/api/agent-sessions/{id}/start", id).with(csrf())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.startedAt").isNotEmpty());
        mvc.perform(post("/api/agent-sessions/{id}/start", id).with(csrf())).andExpect(status().isConflict());
        verify(runner, timeout(3000).times(1)).run(id);
    }
    @Test void realMockCompletesWithOrderedExplicitlyMockEvents() throws Exception {
        UUID id = service.create(serviceId).id();
        doAnswer(call -> { new MockAgentSessionRunner(service, 0).run(id); return null; }).when(runner).run(id);
        service.start(id);
        awaitStatus(id, COMPLETED);
        var result = service.get(id);
        assertEquals(COMPLETED, result.status()); assertNotNull(result.finishedAt());
        assertTrue(result.summary().contains("не собирались"));
        var history = service.events(id);
        assertEquals(List.of(SESSION_CREATED, SESSION_STARTED, DIAGNOSTIC_STARTED, DIAGNOSTIC_STARTED, DIAGNOSTIC_STARTED, SESSION_COMPLETED),
                history.stream().map(e -> e.type()).toList());
        for (int i = 0; i < history.size(); i++) assertEquals(i + 1, history.get(i).sequenceNumber());
        assertThrows(IllegalStateException.class, () -> service.cancel(id));
        assertThrows(IllegalStateException.class, () -> service.start(id));
    }
    @Test void cancelStopsFurtherStagesAndFailureCannotOverwriteIt() {
        UUID id = service.create(serviceId).id(); service.start(id); service.cancel(id);
        new MockAgentSessionRunner(service, 0).run(id); service.fail(id, "safe failure");
        assertEquals(CANCELLED, service.get(id).status()); assertNotNull(service.get(id).finishedAt());
        assertEquals(List.of(SESSION_CREATED, SESSION_STARTED, SESSION_CANCELLED), service.events(id).stream().map(e -> e.type()).toList());
        assertThrows(IllegalStateException.class, () -> service.cancel(id));
    }
    @Test void runningMockObservesCancellationBetweenStages() throws Exception {
        UUID id = service.create(serviceId).id();
        CountDownLatch firstStage = new CountDownLatch(1);
        CountDownLatch continueRunner = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        doAnswer(call -> {
            try {
                service.recordMockStage(id, "Mock: controlled test stage");
                firstStage.countDown();
                await(continueRunner);
                new MockAgentSessionRunner(service, 0).run(id);
            } finally { finished.countDown(); }
            return null;
        }).when(runner).run(id);
        service.start(id);
        try {
            assertTrue(firstStage.await(5, TimeUnit.SECONDS));
            service.cancel(id);
        } finally { continueRunner.countDown(); }
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(CANCELLED, service.get(id).status());
        assertEquals(4, service.events(id).size());
        assertEquals(SESSION_CANCELLED, service.events(id).getLast().type());
    }
    @Test void creationAndItsEventRollBackTogether() {
        UUID[] id = new UUID[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            id[0] = service.create(serviceId).id(); tx.setRollbackOnly();
        });
        assertFalse(sessions.existsById(id[0]));
        assertEquals(0, events.countByAgentSession_Id(id[0]));
    }
    @Test void pendingCanBeCancelled() {
        UUID id = service.create(serviceId).id(); service.cancel(id);
        assertEquals(CANCELLED, service.get(id).status()); assertNull(service.get(id).startedAt());
    }
    @Test void runnerExceptionProducesSafeFailure() throws Exception {
        UUID id = service.create(serviceId).id();
        doThrow(new IllegalStateException("secret-token-test")).when(runner).run(id);
        service.start(id);
        awaitStatus(id, FAILED);
        assertFalse(service.get(id).errorMessage().contains("secret-token-test"));
        assertNotNull(service.get(id).finishedAt());
        assertEquals(SESSION_FAILED, service.events(id).getLast().type());
    }
    @Test void rejectedExecutorIsPersistedAfterCommit() {
        UUID id = service.create(serviceId).id();
        var rejecting = new AgentSessionExecution(runner, service, sessions, task -> { throw new RejectedExecutionException(); });
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.start(id);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { rejecting.afterCommit(new AgentSessionService.StartRequested(id)); }
                    });
        });
        assertEquals(FAILED, service.get(id).status());
    }
    @Test void rollbackDoesNotSubmitRunnerOrPersistStartEvent() throws Exception {
        UUID id = service.create(serviceId).id();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> { service.start(id); tx.setRollbackOnly(); });
        verify(runner, after(150).never()).run(id);
        assertEquals(PENDING, service.get(id).status()); assertEquals(1, service.events(id).size());
    }
    @Test void concurrentCompletionAndCancellationHaveExactlyOneTerminalEvent() throws Exception {
        UUID id = service.create(serviceId).id(); service.start(id);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> complete = pool.submit(() -> { await(go); service.complete(id); });
            Future<?> cancel = pool.submit(() -> {
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication); org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                try { await(go); service.cancel(id); } catch (IllegalStateException expected) { }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            go.countDown(); complete.get(5, TimeUnit.SECONDS); cancel.get(5, TimeUnit.SECONDS);
        }
        assertTrue(Set.of(COMPLETED, CANCELLED).contains(service.get(id).status()));
        assertEquals(1, service.events(id).stream().filter(e -> Set.of(SESSION_COMPLETED, SESSION_CANCELLED).contains(e.type())).count());
    }
    @Test void projectGrantIsRequiredForEveryEndpoint() throws Exception {
        UUID id = service.create(serviceId).id();
        String[] reads = {"/api/services/" + serviceId + "/agent-sessions", "/api/agent-sessions/" + id, "/api/agent-sessions/" + id + "/events"};
        String[] writes = {reads[0], reads[1] + "/start", reads[1] + "/cancel"};
        for (String path : reads) mvc.perform(get(path).with(user("engineer@test.example"))).andExpect(status().isForbidden());
        for (String path : writes) mvc.perform(post(path).with(user("engineer@test.example")).with(csrf())).andExpect(status().isForbidden());
        UUID userId = users.findByEmail("engineer@test.example").orElseThrow().getId();
        jdbc.update("insert into project_metrics_access (id, project_id, user_id) values (?, ?, ?)", UUID.randomUUID(), projectId, userId);
        mvc.perform(get(reads[1]).with(user("engineer@test.example"))).andExpect(status().isOk());
        mvc.perform(post(reads[0]).with(user("engineer@test.example")).with(csrf())).andExpect(status().isCreated());
    }
    @Test void requiresLoginAndCsrfAndIgnoresClientOwnedState() throws Exception {
        String path = "/api/services/" + serviceId + "/agent-sessions";
        mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post(path)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf()).contentType("application/json").content("{\"status\":\"COMPLETED\",\"recoveryAttempts\":99}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recoveryAttempts").value(0));
    }
    @Test void restartReconciliationPreservesTerminalAndPendingSessions() {
        UUID running = service.create(serviceId).id(); service.start(running);
        UUID pending = service.create(serviceId).id();
        UUID cancelled = service.create(serviceId).id(); service.cancel(cancelled);
        execution.reconcile(); execution.reconcile();
        assertEquals(FAILED, service.get(running).status());
        assertEquals(PENDING, service.get(pending).status()); assertEquals(CANCELLED, service.get(cancelled).status());
        assertEquals(1, service.events(running).stream().filter(e -> e.type() == SESSION_FAILED).count());
    }
    @Test void stateMachineCoversAllPairs() {
        for (var from : ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.values()) for (var to : ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.values()) {
            boolean allowed = switch (from) {
                case PENDING -> Set.of(RUNNING, CANCELLED).contains(to);
                case RUNNING -> Set.of(WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED).contains(to);
                case WAITING_APPROVAL -> Set.of(RUNNING, FAILED, CANCELLED).contains(to);
                default -> false;
            };
            assertEquals(allowed, from.canTransitionTo(to), from + " -> " + to);
        }
    }
    private void awaitStatus(UUID id, ru.savostov.sre_platform.model.agentSession.AgentSessionStatus status) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (service.get(id).status() != status && System.nanoTime() < until) Thread.sleep(10);
        assertEquals(status, service.get(id).status());
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }
}
