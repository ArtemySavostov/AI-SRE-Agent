package ru.savostov.sre_platform.logs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.discovery.DiscoveryModels.ConnectionSettings;
import ru.savostov.sre_platform.discovery.ServerSshConnectionRepository;
import ru.savostov.sre_platform.metrics.ProjectMetricsAccess;
import ru.savostov.sre_platform.model.integration.Integration;
import ru.savostov.sre_platform.repository.IntegrationRepository;
import ru.savostov.sre_platform.transport.RemoteEndpoint;
import java.util.*;

@Service
public class LokiSourceStore {
    private final IntegrationRepository integrations;
    private final ServerSshConnectionRepository connections;
    private final ProjectMetricsAccess access;
    public LokiSourceStore(IntegrationRepository integrations, ServerSshConnectionRepository connections,
                           ProjectMetricsAccess access) {
        this.integrations = integrations; this.connections = connections; this.access = access;
    }
    @Transactional(readOnly = true)
    public Source load(UUID projectId, UUID id) {
        access.require(projectId);
        Integration item = integrations.findById(id).filter(i -> i.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NoSuchElementException("Интеграция не найдена"));
        if (item.getType() != Integration.IntegrationType.LOKI)
            throw new IllegalArgumentException("Требуется интеграция Loki");
        if (item.getServer() == null || !item.getServer().getProject().getId().equals(projectId))
            throw new IllegalArgumentException("Проверьте привязку сервера к проекту");
        ConnectionSettings ssh = connections.findById(item.getServer().getId())
                .orElseThrow(() -> new IllegalArgumentException("Сначала настройте SSH-подключение сервера")).settings();
        return new Source(id, item.getServer().getId(), ssh, RemoteEndpoint.parse(item.getBaseUrl()));
    }
    @Transactional
    public void status(Source source, Integration.IntegrationStatus status) {
        integrations.findById(source.id()).ifPresent(item -> {
            if (item.getType() == Integration.IntegrationType.LOKI && item.getServer() != null
                    && item.getServer().getId().equals(source.serverId())
                    && RemoteEndpoint.parse(item.getBaseUrl()).equals(source.endpoint())
                    && connections.findById(source.serverId()).map(c -> c.settings().equals(source.ssh())).orElse(false))
                item.setStatus(status);
        });
    }
    public record Source(UUID id, UUID serverId, ConnectionSettings ssh, RemoteEndpoint endpoint) {}
}
