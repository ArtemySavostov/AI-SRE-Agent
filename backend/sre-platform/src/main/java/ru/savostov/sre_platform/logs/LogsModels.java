package ru.savostov.sre_platform.logs;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LogsModels {
    private LogsModels() {}
    public record Entry(String timestampNs, Map<String, String> labels, String line,
                        Map<String, String> metadata) {}
    public record Result(Instant fetchedAt, Instant start, Instant end, String direction,
                         List<Entry> entries, boolean limitReached, List<String> warnings) {}
    public record TestResult(String status, Instant checkedAt, List<String> warnings) {}
}
