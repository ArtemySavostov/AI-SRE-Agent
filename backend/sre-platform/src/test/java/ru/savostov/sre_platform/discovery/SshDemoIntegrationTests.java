package ru.savostov.sre_platform.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@EnabledIfEnvironmentVariable(named = "SRE_SSH_INTEGRATION", matches = "true")
class SshDemoIntegrationTests {
    @Test
    void readsDemoThroughPinnedSshConnection() {
        try (var deadlines = Executors.newScheduledThreadPool(2)) {
            var ssh = new SshTransport(new SshKeyStore(".local/ssh"), deadlines);
            var probe = new DockerDiscoveryProbe(ssh, JsonMapper.builder().build());
            var settings = new ConnectionSettings("localhost", 2522, "git", "demo_ed25519",
                    System.getenv("SRE_DEMO_FINGERPRINT"));
            assertTrue(probe.test(settings).connected());
            Snapshot snapshot = probe.discover(settings);
            assertTrue(snapshot.host().memoryBytes() > 0);
            assertTrue(snapshot.containers().stream().anyMatch(c -> "app".equals(c.composeService())));
            assertTrue(snapshot.containers().stream().anyMatch(c -> "postgres".equals(c.composeService())));
            System.out.printf("Discovery verified: host=%s, Docker=%s, containers=%d%n",
                    snapshot.host().hostname(), snapshot.docker().version(), snapshot.containers().size());
            var wrongKey = new ConnectionSettings("localhost", 2522, "git", "demo_ed25519", "SHA256:" + "A".repeat(43));
            var failure = assertThrows(DiscoveryException.class, () -> probe.test(wrongKey));
            assertEquals("HOST_KEY_REJECTED", failure.getCode());
        }
    }
}
