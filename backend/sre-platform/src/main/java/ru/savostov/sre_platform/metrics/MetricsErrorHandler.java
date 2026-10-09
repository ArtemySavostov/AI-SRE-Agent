package ru.savostov.sre_platform.metrics;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.discovery.DiscoveryException;
import ru.savostov.sre_platform.controller.IntegrationController;
import java.util.NoSuchElementException;

@RestControllerAdvice(assignableTypes = {MetricsController.class, IntegrationController.class})
public class MetricsErrorHandler {
    public record Error(String code, String message) {}
    @ExceptionHandler(MetricsException.class)
    public ResponseEntity<Error> metrics(MetricsException e) {
        int status = "BUSY".equals(e.getCode()) || "SSH_RETRY_LATER".equals(e.getCode()) ? 503 : 502;
        return ResponseEntity.status(status).body(new Error(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler(DiscoveryException.class)
    public ResponseEntity<Error> ssh(DiscoveryException e) { return ResponseEntity.status(502).body(new Error(e.getCode(), e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Error> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST", e.getMessage())); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Error> missing(NoSuchElementException e) { return ResponseEntity.status(404).body(new Error("NOT_FOUND", e.getMessage())); }
}
