package ru.savostov.sre_platform.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.UUID;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@RestController
@RequestMapping("/api/project/{projectId}/server/{serverId}")
public class ServerDiscoveryController {
    private final DiscoveryService service;
    private final DiscoveryStore store;
    public ServerDiscoveryController(DiscoveryService service, DiscoveryStore store) {
        this.service = service;
        this.store = store;
    }
    @PutMapping("/ssh")
    public ConnectionSettings configure(@PathVariable UUID projectId, @PathVariable UUID serverId,
                                        @RequestBody ConnectionSettings request) {
        return service.configure(projectId, serverId, request);
    }
    @GetMapping("/ssh")
    public ConnectionSettings settings(@PathVariable UUID projectId, @PathVariable UUID serverId) {
        return store.settings(projectId, serverId);
    }
    @PostMapping("/ssh/test")
    public ConnectionTest test(@PathVariable UUID projectId, @PathVariable UUID serverId) {
        return service.test(projectId, serverId);
    }
    @PostMapping("/discovery")
    public ResponseEntity<JobView> discover(@PathVariable UUID projectId, @PathVariable UUID serverId) {
        JobView job = service.discover(projectId, serverId);
        return ResponseEntity.accepted().location(URI.create("/api/project/" + projectId + "/server/" + serverId
                + "/discovery")).body(job);
    }
    @GetMapping("/discovery")
    public JobView latest(@PathVariable UUID projectId, @PathVariable UUID serverId) {
        return store.latest(projectId, serverId);
    }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", exception.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", exception.getMessage()));
    }
    @ExceptionHandler(DiscoveryException.class)
    public ResponseEntity<ErrorResponse> failed(DiscoveryException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "BUSY" -> HttpStatus.CONFLICT;
            case "KEY_UNAVAILABLE", "QUEUE_FULL" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }
}
