package ru.savostov.sre_platform.service.agentSession.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import java.util.UUID;

@Component
public class MockAgentSessionRunner implements AgentSessionRunner {
    private final AgentSessionService service;
    private final long delayMillis;
    public MockAgentSessionRunner(AgentSessionService service, @Value("${sre.agent-session.mock-step-delay-ms:500}") long delayMillis) {
        if (delayMillis < 0) throw new IllegalArgumentException("Mock delay must be non-negative");
        this.service = service; this.delayMillis = delayMillis;
    }
    @Override public void run(UUID id) {
        for (String stage : new String[]{"Mock: подготовка сценария", "Mock: имитация этапа диагностики без доступа к инфраструктуре", "Mock: подготовка тестового итога"}) {
            if (!service.recordMockStage(id, stage)) return;
            try { Thread.sleep(delayMillis); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Mock interrupted", e); }
        }
        service.complete(id);
    }
}
