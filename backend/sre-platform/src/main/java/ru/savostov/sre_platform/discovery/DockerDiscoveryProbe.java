package ru.savostov.sre_platform.discovery;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@Component
public class DockerDiscoveryProbe {
    private final SshTransport ssh;
    private final JsonMapper json;
    public DockerDiscoveryProbe(SshTransport ssh, JsonMapper json) {
        this.ssh = ssh;
        this.json = json;
    }
    public ConnectionTest test(ConnectionSettings settings) {
        HostInfo host = parseHost(ssh.collect(settings, List.of(RemoteCommand.HOST)).get(RemoteCommand.HOST));
        return new ConnectionTest(true, Instant.now(), host.hostname());
    }
    public Snapshot discover(ConnectionSettings settings) {
        var result = ssh.collect(settings, List.of(RemoteCommand.HOST, RemoteCommand.DOCKER, RemoteCommand.CONTAINERS));
        try {
            DockerInfo docker = json.readValue(result.get(RemoteCommand.DOCKER), DockerInfo.class);
            if (docker.version() == null || docker.version().isBlank()) { throw new IllegalArgumentException(); }
            return new Snapshot(Instant.now(), parseHost(result.get(RemoteCommand.HOST)), docker,
                    parseContainers(result.get(RemoteCommand.CONTAINERS)));
        } catch (DiscoveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }
    HostInfo parseHost(String output) {
        try {
            Map<String, String> fields = new HashMap<>();
            output.lines().forEach(line -> {
                int separator = line.indexOf('=');
                if (separator > 0) { fields.put(line.substring(0, separator), line.substring(separator + 1)); }
            });
            HostInfo host = new HostInfo(Objects.requireNonNull(fields.get("hostname")),
                    Objects.requireNonNull(fields.get("operatingSystem")), Objects.requireNonNull(fields.get("kernel")),
                    Objects.requireNonNull(fields.get("architecture")), Integer.parseInt(fields.get("cpuCount")),
                    Long.parseLong(fields.get("memoryBytes")), Double.parseDouble(fields.get("uptimeSeconds")));
            if (host.hostname().isBlank() || host.cpuCount() < 1 || host.memoryBytes() < 1
                    || !Double.isFinite(host.uptimeSeconds()) || host.uptimeSeconds() < 0) {
                throw new IllegalArgumentException();
            }
            return host;
        } catch (RuntimeException exception) { throw invalidResponse(); }
    }
    List<ContainerInfo> parseContainers(String output) {
        try {
            List<ContainerInfo> result = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (String line : output.lines().filter(s -> !s.isBlank()).toList()) {
                JsonNode node = json.readTree(line);
                String id = required(node, "id");
                if (!id.matches("[a-f0-9]{64}") || !ids.add(id) || result.size() >= 1000) {
                    throw new IllegalArgumentException();
                }
                List<PortBinding> ports = new ArrayList<>();
                for (var entry : node.path("ports").properties()) {
                    if (entry.getValue().isArray()) {
                        for (JsonNode binding : entry.getValue()) {
                            ports.add(new PortBinding(entry.getKey(), text(binding, "HostIp"), text(binding, "HostPort")));
                        }
                    } else {
                        ports.add(new PortBinding(entry.getKey(), null, null));
                    }
                }
                List<NetworkInfo> networks = new ArrayList<>();
                for (var entry : node.path("networks").properties()) {
                    networks.add(new NetworkInfo(entry.getKey(), text(entry.getValue(), "IPAddress")));
                }
                String name = required(node, "name").replaceFirst("^/", "");
                result.add(new ContainerInfo(id, name, required(node, "image"), required(node, "state"),
                        text(node, "health"), text(node, "composeProject"), text(node, "composeService"),
                        text(node, "startedAt"), List.copyOf(ports), List.copyOf(networks)));
            }
            result.sort(Comparator.comparing(ContainerInfo::name));
            return List.copyOf(result);
        } catch (RuntimeException exception) { throw invalidResponse(); }
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(); }
        return value;
    }
    private DiscoveryException invalidResponse() {
        return new DiscoveryException("INVALID_RESPONSE", "Сервер вернул некорректные данные discovery");
    }
}
