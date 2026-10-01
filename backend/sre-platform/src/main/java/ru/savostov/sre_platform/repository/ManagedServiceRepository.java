package ru.savostov.sre_platform.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.savostov.sre_platform.model.service.ManagedService;

import java.util.List;
import java.util.UUID;

public interface ManagedServiceRepository extends JpaRepository<ManagedService, UUID> {
    List<ManagedService> findByProject_IdAndServer_Id(UUID projectId, UUID serverId, Sort sort);
}
