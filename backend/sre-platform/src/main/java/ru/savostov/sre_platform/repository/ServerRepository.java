package ru.savostov.sre_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.savostov.sre_platform.model.server.Server;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;

public interface ServerRepository extends JpaRepository<Server, UUID> {
    List<Server> findByProject_Id(UUID projectId, Sort sort);
    Optional<Server> findByIdAndProject_Id(UUID id, UUID projectId);

}
