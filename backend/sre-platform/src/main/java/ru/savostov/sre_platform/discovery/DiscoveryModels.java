package ru.savostov.sre_platform.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DiscoveryModels {
    private DiscoveryModels() { }

    public record ConnectionSettings(String host, int port, String username, String credentialId,
                                     String hostKeyFingerprint) {
        public ConnectionSettings {
            host = normalized(host);
            username = normalized(username);
            credentialId = normalized(credentialId);
            hostKeyFingerprint = normalized(hostKeyFingerprint);
            if (!host.matches("[a-zA-Z0-9._:%-]{1,255}") || port < 1 || port > 65535) {
                throw new IllegalArgumentException("Укажите SSH-адрес и порт от 1 до 65535");
            }
            if (!username.matches("[a-zA-Z_][a-zA-Z0-9_.-]{0,63}")) {
                throw new IllegalArgumentException("Укажите корректного SSH-пользователя");
            }
            if (!credentialId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) {
                throw new IllegalArgumentException("Укажите идентификатор ключа без пути и расширения");
            }
            if (!hostKeyFingerprint.matches("SHA256:[A-Za-z0-9+/]{43}=?")) {
                throw new IllegalArgumentException("Укажите SHA256-отпечаток SSH-ключа сервера");
            }
        }
        private static String normalized(String value) { return value == null ? "" : value.strip(); }
    }
    public record HostInfo(String hostname, String operatingSystem, String kernel, String architecture,
                           int cpuCount, long memoryBytes, double uptimeSeconds) { }
    public record DockerInfo(String version, String name, String storageDriver, int containers,
                             int running, int stopped) { }
    public record PortBinding(String containerPort, String hostIp, String hostPort) { }
    public record NetworkInfo(String name, String ipAddress) { }
    public record ContainerInfo(String id, String name, String image, String state, String health,
                                String composeProject, String composeService, String startedAt,
                                List<PortBinding> ports, List<NetworkInfo> networks) { }
    public record Snapshot(Instant collectedAt, HostInfo host, DockerInfo docker, List<ContainerInfo> containers) { }
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }
    public record JobView(UUID jobId, Status status, Instant requestedAt, Instant completedAt,
                          String errorCode, String error, Snapshot snapshot) { }
    public record ConnectionTest(boolean connected, Instant checkedAt, String hostname) { }
    public record ErrorResponse(String code, String message) { }
}
