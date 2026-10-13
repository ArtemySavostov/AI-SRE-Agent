package ru.savostov.sre_platform.metrics;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import ru.savostov.sre_platform.logs.LogsService;
import ru.savostov.sre_platform.service.IntegrationService;
import ru.savostov.sre_platform.model.integration.Integration.IntegrationType;
import java.util.*;

@RestController
@RequestMapping("/api/project/{projectId}/integration/{integrationId}")
public class MetricsController {
    private final MetricsService metrics;
    private final LogsService logs;
    private final IntegrationService integrations;
    public MetricsController(MetricsService metrics, LogsService logs, IntegrationService integrations) {
        this.metrics = metrics; this.logs = logs; this.integrations = integrations;
    }
    @PostMapping("/test")
    public Object test(@PathVariable UUID projectId, @PathVariable UUID integrationId) {
        return integrations.type(projectId, integrationId) == IntegrationType.LOKI
                ? logs.test(projectId, integrationId) : metrics.test(projectId, integrationId);
    }
    @GetMapping("/metrics/summary")
    public List<MetricsService.MetricResult> summary(@PathVariable UUID projectId, @PathVariable UUID integrationId) {
        return metrics.summary(projectId, integrationId);
    }
    @GetMapping("/metrics/history")
    public MetricsService.MetricResult history(@PathVariable UUID projectId, @PathVariable UUID integrationId,
            @RequestParam String metric, @RequestParam Instant start, @RequestParam Instant end,
            @RequestParam(defaultValue = "600") int points) {
        return metrics.history(projectId, integrationId, metric, start, end, points);
    }
}
