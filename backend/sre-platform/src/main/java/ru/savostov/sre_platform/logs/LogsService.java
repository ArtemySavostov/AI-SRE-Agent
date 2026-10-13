package ru.savostov.sre_platform.logs;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import ru.savostov.sre_platform.discovery.DiscoveryException;
import ru.savostov.sre_platform.metrics.MetricsException;
import ru.savostov.sre_platform.model.integration.Integration.IntegrationStatus;
import ru.savostov.sre_platform.logs.LokiSourceStore.Source;
import ru.savostov.sre_platform.logs.LogsModels.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class LogsService {
    private final LokiSourceStore sources;
    private final LokiClient client;
    private final Semaphore operations = new Semaphore(2);
    public LogsService(LokiSourceStore sources, LokiClient client) { this.sources = sources; this.client = client; }

    public TestResult test(UUID projectId, UUID id) {
        Source source = sources.load(projectId, id);
        return execute(source, () -> {
            client.ready(source);
            return new TestResult("CONNECTED", Instant.now(), List.of(
                    "Loki готов принимать запросы. Проверка не подтверждает поступление логов из Alloy."));
        });
    }

    public Result query(UUID projectId, UUID id, Instant start, Instant end, String service,
                        String container, String composeProject, String search, int limit, String direction) {
        Source source = sources.load(projectId, id);
        validateRange(start, end, limit, direction);
        String expression = expression(service, container, composeProject, search);
        return execute(source, () -> parse(client.query(source, expression, Map.of(
                "start", start.toString(), "end", end.toString(),
                "limit", Integer.toString(limit), "direction", direction)), start, end, limit, direction));
    }

    static void validateRange(Instant start, Instant end, int limit, String direction) {
        if (start == null || end == null || start.isBefore(Instant.EPOCH) || !start.isBefore(end)
                || Duration.between(start, end).compareTo(Duration.ofDays(7)) > 0
                || end.isAfter(Instant.now().plusSeconds(60)))
            throw new IllegalArgumentException("Укажите период до 7 дней: start < end, без будущих дат");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit должен быть от 1 до 1000");
        if (!"backward".equals(direction) && !"forward".equals(direction))
            throw new IllegalArgumentException("direction должен быть backward или forward");
    }

    static String expression(String service, String container, String composeProject, String search) {
        List<String> selectors = new ArrayList<>();
        add(selectors, "service_name", service);
        add(selectors, "container", container);
        add(selectors, "compose_project", composeProject);
        if (selectors.isEmpty()) selectors.add("service_name=~\".+\"");
        String query = "{" + String.join(",", selectors) + "}";
        if (search != null && !search.isEmpty()) {
            if (search.length() > 1024) throw new IllegalArgumentException("Поиск ограничен 1024 символами");
            query += " |= " + quote(search);
        }
        return query;
    }
    private static void add(List<String> selectors, String key, String value) {
        if (value == null || value.isEmpty()) return;
        if (value.isBlank() || value.length() > 255)
            throw new IllegalArgumentException("Метка " + key + " должна содержать от 1 до 255 символов");
        selectors.add(key + "=" + quote(value));
    }
    // LogQL double-quoted strings use Go escapes; never interpolate raw filter text.
    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (Character.isISOControl(ch)) result.append(String.format("\\u%04x", (int) ch));
                    else if (Character.isHighSurrogate(ch)) {
                        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1)))
                            throw new IllegalArgumentException("Некорректный Unicode в фильтре");
                        result.append(ch).append(value.charAt(++i));
                    } else if (Character.isLowSurrogate(ch)) throw new IllegalArgumentException("Некорректный Unicode в фильтре");
                    else result.append(ch);
                }
            }
        }
        return result.append('"').toString();
    }

    static Result parse(JsonNode response, Instant start, Instant end, int limit, String direction) {
        List<Entry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            JsonNode rows = response.path("data").path("result");
            if (!"success".equals(response.path("status").asString(""))
                    || !"streams".equals(response.path("data").path("resultType").asString(""))
                    || !rows.isArray()) throw new IllegalArgumentException();
            for (JsonNode stream : rows) {
                Map<String, String> labels = strings(stream.path("stream"));
                JsonNode values = stream.path("values");
                if (!values.isArray()) throw new IllegalArgumentException();
                for (JsonNode tuple : values) {
                    if (!tuple.isArray() || tuple.size() < 2 || tuple.size() > 3
                            || !tuple.get(0).isString() || !tuple.get(1).isString()) throw new IllegalArgumentException();
                    String timestamp = tuple.get(0).asString();
                    if (!timestamp.matches("[0-9]{1,19}")) throw new IllegalArgumentException();
                    long ns = Long.parseLong(timestamp);
                    Instant at = Instant.ofEpochSecond(ns / 1_000_000_000L, ns % 1_000_000_000L);
                    if (at.isBefore(start) || !at.isBefore(end)) throw new IllegalArgumentException();
                    entries.add(new Entry(timestamp, labels, tuple.get(1).asString(),
                            tuple.size() == 3 ? strings(tuple.get(2)) : Map.of()));
                    if (entries.size() > limit) throw new IllegalArgumentException();
                }
            }
            JsonNode notices = response.path("warnings");
            if (!notices.isMissingNode()) {
                if (!notices.isArray()) throw new IllegalArgumentException();
                for (JsonNode notice : notices) {
                    if (!notice.isString()) throw new IllegalArgumentException();
                    warnings.add(notice.asString());
                }
            }
        } catch (RuntimeException e) {
            throw new MetricsException("INVALID_RESPONSE", "Некорректный ответ Loki: ожидаются потоки логов с временными метками в наносекундах");
        }
        Comparator<Entry> order = Comparator.comparingLong(entry -> Long.parseLong(entry.timestampNs()));
        entries.sort("backward".equals(direction) ? order.reversed() : order);
        boolean limitReached = entries.size() == limit;
        if (limitReached) warnings.add("Достигнут лимит записей. Сузьте период или фильтры; полнота выдачи не гарантируется.");
        return new Result(Instant.now(), start, end, direction, List.copyOf(entries), limitReached, List.copyOf(warnings));
    }
    private static Map<String, String> strings(JsonNode node) {
        if (!node.isObject()) throw new IllegalArgumentException();
        Map<String, String> values = new TreeMap<>();
        for (var property : node.properties()) {
            if (!property.getValue().isString()) throw new IllegalArgumentException();
            values.put(property.getKey(), property.getValue().asString());
        }
        return Collections.unmodifiableMap(values);
    }
    private <T> T execute(Source source, Supplier<T> action) {
        if (!operations.tryAcquire()) throw new MetricsException("BUSY", "Слишком много запросов логов");
        try {
            T result = action.get();
            sources.status(source, IntegrationStatus.CONNECTED);
            return result;
        } catch (MetricsException | DiscoveryException error) {
            // Capacity/backoff errors do not prove that the source is down.
            String code = error instanceof MetricsException m ? m.getCode() : "";
            if (!"BUSY".equals(code) && !"SSH_RETRY_LATER".equals(code))
                sources.status(source, IntegrationStatus.ERROR);
            throw error;
        } finally { operations.release(); }
    }
}
