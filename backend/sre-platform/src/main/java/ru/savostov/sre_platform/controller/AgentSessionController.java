package ru.savostov.sre_platform.controller;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import java.util.*;
import static ru.savostov.sre_platform.service.agentSession.AgentSessionDtos.*;

@RestController
@RequestMapping("/api")
public class AgentSessionController {
    private final AgentSessionService service;
    public AgentSessionController(AgentSessionService service) { this.service = service; }
    // Commands have no body: service-owned fields cannot be assigned by the caller.
    @PostMapping("/services/{serviceId}/agent-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@PathVariable UUID serviceId) { return service.create(serviceId); }
    @PostMapping("/agent-sessions/{id}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SessionResponse start(@PathVariable UUID id) { return service.start(id); }
    @PostMapping("/agent-sessions/{id}/cancel")
    public SessionResponse cancel(@PathVariable UUID id) { return service.cancel(id); }
    @GetMapping("/agent-sessions/{id}")
    public SessionResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/agent-sessions/{id}/events")
    public List<EventResponse> events(@PathVariable UUID id) { return service.events(id); }
    @GetMapping("/services/{serviceId}/agent-sessions")
    public List<SessionResponse> history(@PathVariable UUID serviceId) { return service.history(serviceId); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(NoSuchElementException e) { return ResponseEntity.status(404).body(new ErrorResponse(e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage())); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException e) { return ResponseEntity.status(403).body(new ErrorResponse("Нет доступа к проекту")); }
    public record ErrorResponse(String message) { }
}
