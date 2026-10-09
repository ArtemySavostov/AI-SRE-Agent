package ru.savostov.sre_platform.metrics;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/project/{projectId}/integration/{integrationId}")
public class MetricsController {
    private final MetricsService metrics;
    public MetricsController(MetricsService metrics) { this.metrics = metrics; }
    @PostMapping("/test")
    public MetricsService.TestResult test(@PathVariable UUID projectId, @PathVariable UUID integrationId) {
        return metrics.test(projectId, integrationId);
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
