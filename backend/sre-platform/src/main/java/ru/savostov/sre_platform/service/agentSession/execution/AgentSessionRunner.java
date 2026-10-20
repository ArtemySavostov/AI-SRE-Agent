package ru.savostov.sre_platform.service.agentSession.execution;

import java.util.UUID;

/** Execution boundary for a future external worker. Implementations must respect cancellation. */
public interface AgentSessionRunner {
    void run(UUID sessionId);
}
