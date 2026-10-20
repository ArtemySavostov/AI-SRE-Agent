package ru.savostov.sre_platform.model.agentSession;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.savostov.sre_platform.model.incident.Incident;
import ru.savostov.sre_platform.model.service.ManagedService;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agent_session_service"))
    private ManagedService service;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AgentSessionStatus status = AgentSessionStatus.PENDING;
    @Column(name = "recovery_attempts", nullable = false)
    private int recoveryAttempts;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AgentSession(ManagedService service) { this.service = service; }

    // Called by the transactional service after acquiring the database row lock.
    public void transitionTo(AgentSessionStatus next, String summary, String errorMessage) {
        if (!status.canTransitionTo(next)) throw new IllegalStateException("Недопустимый переход состояния сессии");
        if (next == AgentSessionStatus.RUNNING && startedAt == null) startedAt = LocalDateTime.now();
        if (next == AgentSessionStatus.COMPLETED || next == AgentSessionStatus.FAILED || next == AgentSessionStatus.CANCELLED) {
            finishedAt = LocalDateTime.now();
        }
        this.status = next;
        this.summary = summary;
        this.errorMessage = errorMessage;
    }
}
