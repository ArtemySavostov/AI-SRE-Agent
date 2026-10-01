package ru.savostov.sre_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.service.ProjectService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectService.getProjects().stream()
                .map(project -> new ProjectResponse(project.getId(), project.getName(), project.getDescription()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody CreateProjectRequest request) {
        try {
            Project project = projectService.createProject(request.name(), request.description());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ProjectResponse(project.getId(), project.getName(), project.getDescription()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        }
    }

    public record CreateProjectRequest(String name, String description) { }
    public record ProjectResponse(UUID id, String name, String description) { }
    public record ErrorResponse(String message) { }
}
