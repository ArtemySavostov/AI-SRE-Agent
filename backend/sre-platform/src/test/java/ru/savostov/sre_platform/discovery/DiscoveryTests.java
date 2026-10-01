package ru.savostov.sre_platform.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

class DiscoveryTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private final SshTransport ssh = mock(SshTransport.class);
    private final DockerDiscoveryProbe probe = new DockerDiscoveryProbe(ssh, json);
    private final String fingerprint = "SHA256:" + "A".repeat(43);
    @TempDir Path directory;

    @Test
    void rejectsInvalidConnectionSettings() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("host", 0, "git", "demo", fingerprint));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("host", 22, "git", "../secret", fingerprint));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionSettings("host", 22, "git", "demo", "accept-any"));
    }
    @Test
    void keyStoreRejectsTraversalAndEscapingSymlinks() throws Exception {
        Path root = Files.createDirectory(directory.resolve("keys"));
        Path outside = Files.writeString(directory.resolve("outside"), "secret");
        Files.createSymbolicLink(root.resolve("escape"), outside);
        SshKeyStore keys = new SshKeyStore(root.toString());
        assertThrows(IllegalArgumentException.class, () -> keys.resolve("../outside"));
        assertThrows(DiscoveryException.class, () -> keys.resolve("escape"));
        assertThrows(DiscoveryException.class, () -> keys.resolve("missing"));
        Path valid = Files.writeString(root.resolve("demo"), "key");
        assertEquals(valid.toRealPath(), keys.resolve("demo"));
    }
    @Test
    void limitsRemoteOutputBeforePersistingIt() throws Exception {
        assertEquals("abc", SshTransport.readLimited(new ByteArrayInputStream("abc".getBytes()), 3));
        assertThrows(DiscoveryException.class,
                () -> SshTransport.readLimited(new ByteArrayInputStream("abcd".getBytes()), 3));
    }
    @Test
    void parsesRunningAndStoppedContainersWithoutLeakingExtraFields() {
        String running = """
                {"id":"%s","name":"/demo-app-1","image":"app:v1","state":"running","health":"healthy",
                 "composeProject":"demo","composeService":"app","startedAt":"2026-10-01T00:00:00Z",
                 "ports":{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"18080"}],"9999/tcp":null},
                 "networks":{"demo_default":{"IPAddress":"172.18.0.2","Secret":"hidden"}},
                 "Env":["PASSWORD=secret"]}
                """.formatted("a".repeat(64));
        String stopped = """
                {"id":"%s","name":"/init","image":"alpine","state":"exited","health":null,"ports":{},"networks":{}}
                """.formatted("b".repeat(64));
        running = json.writeValueAsString(json.readTree(running)) + "\n";
        stopped = json.writeValueAsString(json.readTree(stopped)) + "\n";
        final String duplicate = running + running;
        var result = probe.parseContainers(running + stopped);
        assertEquals(2, result.size());
        assertEquals("healthy", result.getFirst().health());
        assertEquals("18080", result.getFirst().ports().getFirst().hostPort());
        assertEquals("172.18.0.2", result.getFirst().networks().getFirst().ipAddress());
        assertEquals("exited", result.get(1).state());
        assertFalse(json.writeValueAsString(result).contains("secret"));
        assertEquals(List.of(), probe.parseContainers(""));
        assertThrows(DiscoveryException.class, () -> probe.parseContainers(duplicate));
        assertThrows(DiscoveryException.class, () -> probe.parseContainers("not JSON"));
    }
    @Test
    void successfulEmptyDockerInventoryIsDifferentFromCommandFailure() {
        ConnectionSettings settings = new ConnectionSettings("host", 22, "git", "demo", fingerprint);
        String host = "hostname=demo\noperatingSystem=Ubuntu\nkernel=6.8\narchitecture=x86_64\ncpuCount=2\nmemoryBytes=4096\nuptimeSeconds=123.5\n";
        when(ssh.collect(eq(settings), anyList())).thenReturn(Map.of(RemoteCommand.HOST, host,
                RemoteCommand.DOCKER,"{\"version\":\"24.0.2\",\"name\":\"demo\",\"containers\":0,\"storageDriver\":\"overlay2\",\"running\":0,\"stopped\":0}", RemoteCommand.CONTAINERS,""));
        Snapshot snapshot = probe.discover(settings);
        assertEquals("Ubuntu", snapshot.host().operatingSystem());
        assertTrue(snapshot.containers().isEmpty());
        when(ssh.collect(eq(settings), anyList())).thenThrow(new DiscoveryException("SSH_FAILED", "Unavailable"));
        assertThrows(DiscoveryException.class, () -> probe.discover(settings));
    }
    @Test
    void rejectsIncompleteHostInfo() {
        assertThrows(DiscoveryException.class, () -> probe.parseHost("hostname=demo"));
    }
}
