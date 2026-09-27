package ru.savostov.sre_platform.model.service;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.server.Server;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "server_id", nullable = true)
    private Server server;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "healthcheck_url", nullable = true, length = 255)
    private String healthcheckUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServiceStatus status = ServiceStatus.UNKNOWN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ServiceStatus {
        UP, DOWN, DEGRADED, UNKNOWN
    }
}
