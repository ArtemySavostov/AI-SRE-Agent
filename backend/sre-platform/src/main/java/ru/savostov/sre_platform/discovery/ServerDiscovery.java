package ru.savostov.sre_platform.discovery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.savostov.sre_platform.model.server.Server;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "server_discovery")
@Getter @Setter @NoArgsConstructor
public class ServerDiscovery {
    @Id
    private UUID serverId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id")
    private Server server;
    @Column(nullable = false)
    private UUID jobId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscoveryModels.Status status;
    @Column(nullable = false)
    private Instant requestedAt;
    private Instant completedAt;
    @Column(length = 64)
    private String errorCode;
    @Column(length = 512)
    private String error;
    @Column(columnDefinition = "text")
    private String snapshotJson;
}
