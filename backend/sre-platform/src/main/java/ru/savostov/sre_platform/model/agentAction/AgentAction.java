package ru.savostov.sre_platform.model.agentAction;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.savostov.sre_platform.model.agentSession.AgentSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_action")
@Getter
@Setter
@NoArgsConstructor
public class AgentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ActionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = true, length = 255)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActionStatus status = ActionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "started_at", nullable = true)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = true)
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ActionType {
        READ_METRICS, READ_LOGS, HEALTH_CHECK, GET_STATUS, RESTART_PROCESS, RESTART_CONTAINER
    }

    public enum TargetType {
        SERVICE, SERVER, CONTAINER, PROCESS
    }

    public enum ActionStatus {
        PENDING, RUNNING, SUCCESS, FAILED, REJECTED
    }

    public enum RiskLevel {
        SAFE, LOW, MEDIUM, HIGH
    }
}
