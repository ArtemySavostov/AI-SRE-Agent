package ru.savostov.sre_platform.model.hypothesis;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import ru.savostov.sre_platform.model.agentSession.AgentSession;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hypothesis")
@Getter
@Setter
@NoArgsConstructor
public class Hypothesis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Min(1)
    @Column(name = "rank", nullable = false)
    private Integer rank;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
