package ru.savostov.sre_platform.service.agentSession.execution;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "sre.agent-session.provider", havingValue = "openrouter")
public class OpenRouterAgentSessionRunner implements AgentSessionRunner {
    private final AgentSessionService service;
    private final OpenRouterClient client;

    public OpenRouterAgentSessionRunner(AgentSessionService service, OpenRouterClient client) {
        this.service = service;
        this.client = client;
    }

    @Override public void run(UUID id) {
        if (!service.recordStage(id, "Исполнитель: OpenRouter; модель: " + client.model()
                + ". Запрошен общий план диагностики. Данные инфраструктуры не передаются.")) return;
        try {
            String result = client.generate();
            // Completion locks the row and discards late results after cancellation.
            service.complete(id, result);
        } catch (OpenRouterClient.Failure e) {
            service.fail(id, e.getMessage());
        }
    }
}
