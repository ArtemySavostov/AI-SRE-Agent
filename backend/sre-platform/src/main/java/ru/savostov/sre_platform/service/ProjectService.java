package ru.savostov.sre_platform.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.repository.ProjectRepository;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> getProjects() {
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    public Project createProject(String name, String description) {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Заполните все поля");
        }
        String normalizedName = name.strip();

        Project project = new Project();
        project.setName(normalizedName);
        project.setDescription(description.strip());

        return projectRepository.save(project);
    }
}
