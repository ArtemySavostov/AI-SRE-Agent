package ru.savostov.sre_platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.service.ServerService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/{projectId}/server")
public class ServerController {
    private final ServerService service;

    public ServerController(ServerService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServerResponse> getServers(@PathVariable UUID projectId) {
        return service.getServers(projectId).stream()
                .map(item -> toResponse(item, projectId))
                .toList();
    }

    @PostMapping
    public ResponseEntity<ServerResponse> createServer(@PathVariable UUID projectId,
            @RequestBody CreateServerRequest request) {
        Server item = service.createServer(projectId, request.name(), request.hostname(), request.ipAddress(), request.os());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item, projectId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    private ServerResponse toResponse(Server item, UUID projectId) {
        return new ServerResponse(item.getId(), projectId, item.getName(), item.getHostname(), item.getIpAddress(), item.getOs(), item.getStatus());
    }

    public record CreateServerRequest(String name, String hostname, String ipAddress, String os) { }
    public record ServerResponse(UUID id, UUID projectId, String name, String hostname, String ipAddress, String os, Server.ServerStatus status) { }
    public record ErrorResponse(String message) { }
}
