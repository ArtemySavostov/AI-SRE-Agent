package ru.savostov.sre_platform.service.agentSession.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.savostov.sre_platform.repository.AgentSessionRepository;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import java.util.List;
import java.util.UUID;
import static ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.*;

@Component
public class AgentSessionExecution {
    private static final Logger log = LoggerFactory.getLogger(AgentSessionExecution.class);
    private final AgentSessionRunner runner;
    private final AgentSessionService service;
    private final AgentSessionRepository sessions;
    private final TaskExecutor executor;
    public AgentSessionExecution(AgentSessionRunner runner, AgentSessionService service, AgentSessionRepository sessions,
            @Qualifier("agentSessionExecutor") TaskExecutor executor) {
        this.runner = runner; this.service = service; this.sessions = sessions; this.executor = executor;
    }
    @TransactionalEventListener
    public void afterCommit(AgentSessionService.StartRequested event) {
        try { executor.execute(() -> execute(event.sessionId())); }
        catch (RuntimeException e) { fail(event.sessionId(), e); }
    }
    private void execute(UUID id) {
        try { runner.run(id); }
        catch (Exception e) { fail(id, e); }
    }
    private void fail(UUID id, Exception error) {
        log.error("Agent session {} execution failed", id, error);
        service.fail(id, "Не удалось выполнить тестовый запуск агента");
    }
    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        for (UUID id : sessions.findIdsByStatusIn(List.of(RUNNING, WAITING_APPROVAL))) {
            service.fail(id, "Исполнение прервано перезапуском backend");
        }
    }
}
