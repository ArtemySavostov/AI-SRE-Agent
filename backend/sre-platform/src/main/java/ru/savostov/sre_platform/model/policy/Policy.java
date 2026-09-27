package ru.savostov.sre_platform.model.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import ru.savostov.sre_platform.model.agentAction.AgentAction;
import ru.savostov.sre_platform.model.project.Project;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "policy")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private AgentAction.ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private ActionPermission permission = ActionPermission.FORBIDDEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ActionPermission {
        AUTO, REQUIRE_APPROVAL, FORBIDDEN
    }
}
