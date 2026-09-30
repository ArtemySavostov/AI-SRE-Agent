package ru.savostov.sre_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.savostov.sre_platform.model.project.Project;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByName(String name);
}
