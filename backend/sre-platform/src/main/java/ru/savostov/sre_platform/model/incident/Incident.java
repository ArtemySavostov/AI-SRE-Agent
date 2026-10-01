package ru.savostov.sre_platform.model.incident;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.service.ManagedService;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident")
@Getter
@Setter
@NoArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ManagedService managedService;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status = IncidentStatus.CREATED;

    @Column(name = "root_cause_summary", nullable = true, columnDefinition = "text")
    private String rootCauseSummary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "resolved_at", nullable = true)
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum IncidentStatus {
        CREATED, INVESTIGATING, WAITING_APPROVAL, EXECUTING_ACTION, VERIFYING, RESOLVED, ESCALATED
    }
}
