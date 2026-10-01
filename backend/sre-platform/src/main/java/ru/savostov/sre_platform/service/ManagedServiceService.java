package ru.savostov.sre_platform.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.repository.ManagedServiceRepository;
import ru.savostov.sre_platform.repository.ServerRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ManagedServiceService {
    private final ManagedServiceRepository managedServiceRepository;
    private final ServerRepository serverRepository;

    public ManagedServiceService(ManagedServiceRepository managedServiceRepository, ServerRepository serverRepository) {
        this.managedServiceRepository = managedServiceRepository;
        this.serverRepository = serverRepository;
    }

    public List<ManagedService> getServices(UUID projectId, UUID serverId) {
        getServer(projectId, serverId);
        return managedServiceRepository.findByProject_IdAndServer_Id(projectId, serverId,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Transactional
    public ManagedService createService(UUID projectId, UUID serverId, String name,
                                        String description, String healthcheckUrl) {
        Server server = getServer(projectId, serverId);
        if (name == null || name.isBlank() || name.strip().length() > 255) {
            throw new IllegalArgumentException("Укажите название сервиса длиной не более 255 символов");
        }
        String normalizedUrl = healthcheckUrl == null || healthcheckUrl.isBlank() ? null : healthcheckUrl.strip();
        if (normalizedUrl != null && normalizedUrl.length() > 255) {
            throw new IllegalArgumentException("URL проверки здоровья должен содержать не более 255 символов");
        }
        ManagedService service = new ManagedService();
        service.setServer(server);
        service.setProject(server.getProject());
        service.setName(name.strip());
        service.setDescription(description == null ? null : description.strip());
        service.setHealthcheckUrl(normalizedUrl);
        return managedServiceRepository.save(service);
    }

    private Server getServer(UUID projectId, UUID serverId) {
        return serverRepository.findByIdAndProject_Id(serverId, projectId)
                .orElseThrow(() -> new NoSuchElementException("Сервер не найден в указанном проекте"));
    }
}
