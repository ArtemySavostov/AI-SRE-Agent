package ru.savostov.sre_platform.logs;

import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/{projectId}/integration/{integrationId}/logs")
public class LogsController {
    private final LogsService logs;
    public LogsController(LogsService logs) { this.logs = logs; }
    @GetMapping
    public LogsModels.Result query(@PathVariable UUID projectId, @PathVariable UUID integrationId,
            @RequestParam Instant start, @RequestParam Instant end,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String container,
            @RequestParam(required = false) String composeProject,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "backward") String direction) {
        return logs.query(projectId, integrationId, start, end, service, container, composeProject,
                search, limit, direction);
    }
}
