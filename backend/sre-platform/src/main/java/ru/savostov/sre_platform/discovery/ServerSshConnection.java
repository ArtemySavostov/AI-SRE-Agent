package ru.savostov.sre_platform.discovery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.savostov.sre_platform.model.server.Server;
import java.util.UUID;

@Entity
@Table(name = "server_ssh_connection")
@Getter @Setter @NoArgsConstructor
public class ServerSshConnection {
    @Id
    private UUID serverId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id")
    private Server server;
    @Column(nullable = false, length = 255)
    private String host;
    @Column(nullable = false)
    private int port;
    @Column(nullable = false, length = 64)
    private String username;
    @Column(nullable = false, length = 64)
    private String credentialId;
    @Column(nullable = false, length = 64)
    private String hostKeyFingerprint;

    public DiscoveryModels.ConnectionSettings settings() {
        return new DiscoveryModels.ConnectionSettings(host, port, username, credentialId, hostKeyFingerprint);
    }
}
