package ru.savostov.sre_platform.service.agentSession;

import ru.savostov.sre_platform.model.agentSession.*;
import ru.savostov.sre_platform.model.agentEvent.*;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AgentSessionDtos {
    private AgentSessionDtos() { }
    public record SessionResponse(UUID id, UUID serviceId, UUID incidentId, AgentSessionStatus status,
            LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            int recoveryAttempts, String summary, String errorMessage) {
        public static SessionResponse from(AgentSession s) {
            return new SessionResponse(s.getId(), s.getService().getId(), s.getIncident() == null ? null : s.getIncident().getId(),
                    s.getStatus(), s.getStartedAt(), s.getFinishedAt(), s.getCreatedAt(), s.getUpdatedAt(),
                    s.getRecoveryAttempts(), s.getSummary(), s.getErrorMessage());
        }
    }
    public record EventResponse(UUID id, AgentEventType type, String message, LocalDateTime createdAt, long sequenceNumber) {
        public static EventResponse from(AgentEvent e) {
            return new EventResponse(e.getId(), e.getType(), e.getMessage(), e.getCreatedAt(), e.getSequenceNumber());
        }
    }
}
