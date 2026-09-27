package ru.savostov.sre_platform.model.agentSession;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.savostov.sre_platform.model.incident.Incident;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_session")
@Getter
@Setter
@NoArgsConstructor
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AgentSessionStatus status = AgentSessionStatus.RUNNING;

    @Min(1)
    @Column(name = "attempt", nullable = false)
    private Integer attempt = 1;

    @Column(name = "summary", nullable = true, columnDefinition = "text")
    private String summary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = true)
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AgentSessionStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }
}
