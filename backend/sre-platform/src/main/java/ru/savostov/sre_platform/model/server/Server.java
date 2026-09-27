package ru.savostov.sre_platform.model.server;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.savostov.sre_platform.model.project.Project;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "server")
@Getter
@Setter
@NoArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "os", nullable = false, length = 100)
    private String os;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServerStatus status = ServerStatus.INACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ServerStatus {
        ACTIVE, INACTIVE
    }
}
