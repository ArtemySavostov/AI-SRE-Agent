package ru.savostov.sre_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.model.integration.Integration;
import ru.savostov.sre_platform.service.IntegrationService;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/{projectId}/integration")
public class IntegrationController {
    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping
    public List<IntegrationResponse> getIntegrations(
            @PathVariable UUID projectId) {
        return integrationService.getIntegrationsByProjectId(projectId)
                .stream()
                .map(item -> new IntegrationResponse(
                        item.getId(),
                        item.getName(),
                        item.getType(),
                        item.getBaseUrl(),
                        item.getStatus(),
                        item.getServer() == null ? null : item.getServer().getId(),
                        item.getConfig()
                ))
                .toList();
    }

    public record IntegrationResponse(
            UUID id,
            String name,
            Integration.IntegrationType type,
            String baseUrl,
            Integration.IntegrationStatus status,
            UUID serverId,
            Map<String, Object> config
    ) {}

    @PostMapping
    public ResponseEntity<IntegrationResponse> createIntegration(@PathVariable UUID projectId,
                                                                 @RequestBody CreateIntegrationRequest request) {
        if (request.serverId() == null) {
            throw new IllegalArgumentException("Укажите сервер");
        }
        Integration item = integrationService.createPrometheusIntegration(
                projectId, request.serverId(), request.name(), request.baseUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(new IntegrationResponse(
                item.getId(), item.getName(), item.getType(), item.getBaseUrl(), item.getStatus(),
                item.getServer().getId(), item.getConfig()));
    }

    @PutMapping("/{integrationId}")
    public IntegrationResponse updateIntegration(@PathVariable UUID projectId, @PathVariable UUID integrationId,
            @RequestBody UpdateIntegrationRequest request) {
        Integration item = integrationService.updatePrometheusIntegration(projectId, integrationId,
                request.name(), request.baseUrl(), request.nodeJob(), request.nodeInstance(),
                request.containerJob(), request.containerInstance());
        return new IntegrationResponse(item.getId(), item.getName(), item.getType(), item.getBaseUrl(),
                item.getStatus(), item.getServer().getId(), item.getConfig());
    }

    public record UpdateIntegrationRequest(String name, String baseUrl, String nodeJob, String nodeInstance,
                                            String containerJob, String containerInstance) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    public record CreateIntegrationRequest(UUID serverId, String name, String baseUrl) {}

    public record ErrorResponse(String message) {}

}
