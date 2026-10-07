package ru.savostov.sre_platform.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.discovery.DiscoveryModels.ConnectionSettings;
import ru.savostov.sre_platform.discovery.ServerSshConnectionRepository;
import ru.savostov.sre_platform.model.integration.Integration;
import ru.savostov.sre_platform.repository.IntegrationRepository;
import java.util.*;

@Service
public class PrometheusSourceStore {
    private final IntegrationRepository integrations;
    private final ServerSshConnectionRepository connections;
    private final ProjectMetricsAccess access;
    public PrometheusSourceStore(IntegrationRepository integrations, ServerSshConnectionRepository connections,
                                 ProjectMetricsAccess access) {
        this.integrations = integrations; this.connections = connections; this.access = access;
    }
    @Transactional(readOnly = true)
    public Source load(UUID projectId, UUID id) {
        access.require(projectId);
        Integration item = integrations.findById(id).filter(i -> i.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NoSuchElementException("Интеграция не найдена"));
        if (item.getType() != Integration.IntegrationType.PROMETHEUS) throw new IllegalArgumentException("Требуется интеграция Prometheus");
        if (item.getServer() == null || !item.getServer().getProject().getId().equals(projectId))
            throw new IllegalArgumentException("Проверьте привязку интеграции к серверу проекта");
        ConnectionSettings settings = connections.findById(item.getServer().getId())
                .orElseThrow(() -> new IllegalArgumentException("Сначала настройте SSH-подключение сервера")).settings();
        Map<String, Object> config = item.getConfig() == null ? Map.of() : item.getConfig();
        return new Source(id, settings, PrometheusEndpoint.parse(item.getBaseUrl()),
                label(config, "nodeJob", "node-exporter"), label(config, "nodeInstance", "node-exporter:9100"),
                label(config, "containerJob", "cadvisor"), label(config, "containerInstance", "cadvisor:8080"));
    }
    private String label(Map<String, Object> config, String key, String fallback) {
        Object value = config.getOrDefault(key, fallback);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 255)
            throw new IllegalArgumentException("Некорректная метка " + key);
        return text;
    }
    @Transactional
    public void status(Source source, Integration.IntegrationStatus status) {
        integrations.findById(source.id()).ifPresent(item -> {
            Map<String, Object> config = item.getConfig() == null ? Map.of() : item.getConfig();
            if (item.getServer() != null && PrometheusEndpoint.parse(item.getBaseUrl()).equals(source.endpoint())
                    && label(config, "nodeJob", "node-exporter").equals(source.nodeJob())
                    && label(config, "nodeInstance", "node-exporter:9100").equals(source.nodeInstance())
                    && label(config, "containerJob", "cadvisor").equals(source.containerJob())
                    && label(config, "containerInstance", "cadvisor:8080").equals(source.containerInstance())
                    && connections.findById(item.getServer().getId()).map(connection -> connection.settings().equals(source.ssh())).orElse(false)) {
                item.setStatus(status);
            }
        });
    }
    public record Source(UUID id, ConnectionSettings ssh, PrometheusEndpoint endpoint,
                         String nodeJob, String nodeInstance, String containerJob, String containerInstance) {}
}
