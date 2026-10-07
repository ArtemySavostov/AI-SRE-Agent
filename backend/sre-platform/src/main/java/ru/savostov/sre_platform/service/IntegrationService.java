package ru.savostov.sre_platform.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.model.integration.Integration;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.repository.IntegrationRepository;
import ru.savostov.sre_platform.repository.ProjectRepository;
import ru.savostov.sre_platform.repository.ServerRepository;
import ru.savostov.sre_platform.metrics.ProjectMetricsAccess;
import ru.savostov.sre_platform.metrics.PrometheusEndpoint;
import ru.savostov.sre_platform.metrics.SshTunnelManager;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class IntegrationService {
    private final IntegrationRepository integrationRepository;
    private final ProjectRepository projectRepository;
    private final ServerRepository serverRepository;
    private final ProjectMetricsAccess access;
    private final SshTunnelManager tunnels;

    public IntegrationService(IntegrationRepository integrationRepository,
                              ProjectRepository projectRepository,
                              ServerRepository serverRepository, ProjectMetricsAccess access, SshTunnelManager tunnels) {
        this.integrationRepository = integrationRepository;
        this.projectRepository = projectRepository;
        this.serverRepository = serverRepository;
        this.access = access;
        this.tunnels = tunnels;
    }

    @Transactional(readOnly = true)
    public List<Integration> getIntegrationsByProjectId(UUID projectId) {
        access.require(projectId);
        return integrationRepository.findAllByProject_Id(projectId);
    }

    @Transactional
    public Integration createPrometheusIntegration(UUID projectId, UUID serverId, String name, String baseUrl) {
        access.require(projectId);
        if (serverId == null) throw new IllegalArgumentException("Укажите сервер");
        if (name == null || name.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Заполните все поля");
        }
        String normalizedName = name.strip();
        validate(name, baseUrl);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Проект не найден"));
        Server server = serverRepository.lockInProject(serverId, projectId)
                .orElseThrow(() -> new NoSuchElementException("Сервер не найден в проекте"));
        if (integrationRepository.findByServer_IdAndType(serverId, Integration.IntegrationType.PROMETHEUS).isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Для сервера уже существует Prometheus-интеграция; используйте PUT");
        }
        Integration integration = new Integration();
        integration.setName(normalizedName);
        integration.setProject(project);
        integration.setServer(server);
        integration.setBaseUrl(baseUrl.strip());
        integration.setType(Integration.IntegrationType.PROMETHEUS);
        integration.setConfig(Map.of("nodeJob", "node-exporter", "nodeInstance", "node-exporter:9100",
                "containerJob", "cadvisor", "containerInstance", "cadvisor:8080"));

        return integrationRepository.save(integration);
    }

    @Transactional
    public Integration updatePrometheusIntegration(UUID projectId, UUID id, String name, String baseUrl,
            String nodeJob, String nodeInstance, String containerJob, String containerInstance) {
        access.require(projectId);
        validate(name, baseUrl);
        Integration integration = integrationRepository.findById(id)
                .filter(item -> item.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NoSuchElementException("Интеграция не найдена"));
        if (integration.getType() != Integration.IntegrationType.PROMETHEUS)
            throw new IllegalArgumentException("Требуется интеграция Prometheus");
        for (String label : new String[]{nodeJob, nodeInstance, containerJob, containerInstance}) {
            if (label == null || label.isBlank() || label.length() > 255 || label.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Укажите job и instance для Node Exporter и cAdvisor (до 255 символов)");
        }
        integration.setName(name.strip());
        integration.setBaseUrl(baseUrl.strip());
        integration.setConfig(Map.of("nodeJob", nodeJob, "nodeInstance", nodeInstance,
                "containerJob", containerJob, "containerInstance", containerInstance));
        integration.setStatus(Integration.IntegrationStatus.DISCONNECTED);
        tunnels.invalidate(id);
        return integrationRepository.save(integration);
    }

    private void validate(String name, String baseUrl) {
        if (name == null || name.isBlank() || name.strip().length() > 255
                || baseUrl == null || baseUrl.length() > 255) throw new IllegalArgumentException("Проверьте название и адрес (до 255 символов)");
        PrometheusEndpoint.parse(baseUrl);
    }
}
