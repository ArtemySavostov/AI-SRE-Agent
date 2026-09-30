package ru.savostov.sre_platform.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.repository.ProjectRepository;
import ru.savostov.sre_platform.repository.ServerRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ServerService {
    private final ServerRepository serverRepository;
    private final ProjectRepository projectRepository;

    public ServerService(ServerRepository serverRepository, ProjectRepository projectRepository) {
        this.serverRepository = serverRepository;
        this.projectRepository = projectRepository;
    }

    public List<Server> getServers(UUID projectId) {
        getProject(projectId);
        return serverRepository.findByProject_Id(projectId, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Transactional
    public Server createServer(UUID projectId, String name, String hostname, String ipAddress, String os) {
        Project project = getProject(projectId);
        Server server = new Server();
        server.setProject(project);
        server.setName(required(name, 255, "Название"));
        server.setHostname(required(hostname, 255, "Hostname"));
        server.setIpAddress(required(ipAddress, 64, "IP-адрес"));
        server.setOs(required(os, 100, "Операционная система"));
        return serverRepository.save(server);
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Проект не найден"));
    }

    private String required(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + ": заполните обязательное поле");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + ": максимум " + maxLength + " символов");
        }
        return normalized;
    }
}
