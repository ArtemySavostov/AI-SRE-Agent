package ru.savostov.sre_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.service.ManagedServiceService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/{projectId}/server/{serverId}/service")
public class ManagedServiceController {
    private final ManagedServiceService service;

    public ManagedServiceController(ManagedServiceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedServiceResponse> getServices(@PathVariable UUID projectId, @PathVariable UUID serverId) {
        return service.getServices(projectId, serverId).stream()
                .map(item -> toResponse(item, projectId, serverId))
                .toList();
    }

    @PostMapping
    public ResponseEntity<ManagedServiceResponse> createService(@PathVariable UUID projectId, @PathVariable UUID serverId,
            @RequestBody CreateManagedServiceRequest request) {
        ManagedService item = service.createService(projectId, serverId, request.name(), request.description(), request.healthcheckUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item, projectId, serverId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    private ManagedServiceResponse toResponse(ManagedService item, UUID projectId, UUID serverId) {
        return new ManagedServiceResponse(item.getId(), projectId, serverId, item.getName(), item.getDescription(), item.getHealthcheckUrl(), item.getStatus());
    }

    public record CreateManagedServiceRequest(String name, String description, String healthcheckUrl) { }
    public record ManagedServiceResponse(UUID id, UUID projectId, UUID serverId, String name, String description, String healthcheckUrl, ManagedService.ServiceStatus status) { }
    public record ErrorResponse(String message) { }
}
