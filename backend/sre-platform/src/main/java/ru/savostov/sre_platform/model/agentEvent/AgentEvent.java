package ru.savostov.sre_platform.model.agentEvent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;
import ru.savostov.sre_platform.model.agentSession.AgentSession;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_event", uniqueConstraints = @UniqueConstraint(name = "uq_agent_event_session_sequence", columnNames = {"agent_session_id", "sequence_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_session_id", nullable = false)
    private AgentSession agentSession;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AgentEventType type;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    public AgentEvent(AgentSession session, AgentEventType type, String message, long sequenceNumber) {
        this.agentSession = session; this.type = type; this.message = message; this.sequenceNumber = sequenceNumber;
    }
}
