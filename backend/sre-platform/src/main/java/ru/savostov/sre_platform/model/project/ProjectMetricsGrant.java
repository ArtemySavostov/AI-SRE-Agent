package ru.savostov.sre_platform.model.project;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.savostov.sre_platform.model.user.User;
import java.util.UUID;

@Entity
@Table(name = "project_metrics_access", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class ProjectMetricsGrant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
