package ru.savostov.sre_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.savostov.sre_platform.model.integration.Integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Optional<Integration> findByName(String name);

    List<Integration> findAllByProject_Id(UUID projectId);

    Optional<Integration> findByServer_IdAndType(UUID serverId, Integration.IntegrationType type);
}
