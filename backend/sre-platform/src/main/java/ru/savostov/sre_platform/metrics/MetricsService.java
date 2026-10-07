package ru.savostov.sre_platform.metrics;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import ru.savostov.sre_platform.model.integration.Integration.IntegrationStatus;
import ru.savostov.sre_platform.metrics.PrometheusSourceStore.Source;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class MetricsService {
    private final PrometheusSourceStore sources;
    private final PrometheusClient client;
    private final Semaphore operations = new Semaphore(4);
    private final Map<CacheKey, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>());
    public MetricsService(PrometheusSourceStore sources, PrometheusClient client) { this.sources = sources; this.client = client; }

    public TestResult test(UUID projectId, UUID id) {
        Source source = sources.load(projectId, id);
        synchronized (cache) { cache.keySet().removeIf(key -> key.source().id().equals(id)); }
        return execute(source, () -> {
            client.ready(source);
            JsonNode response = client.query(source, signedScrapeTime("up"), Map.of());
            List<Target> targets = new ArrayList<>();
            for (JsonNode row : response.path("data").path("result")) {
                Point point = point(row.path("value"));
                Map<String, String> labels = labels(row.path("metric"));
                long sampledAt = point.value() == null ? 0 : (long) (Math.abs(point.value()) * 1000);
                targets.add(new Target(labels, point.value() != null && point.value() > 0,
                        sampledAt, Instant.now().toEpochMilli() - sampledAt > 60_000));
            }
            return new TestResult("CONNECTED", Instant.now(), targets, warnings(response));
        });
    }

    public List<MetricResult> summary(UUID projectId, UUID id) {
        Source source = sources.load(projectId, id);
        long now = Instant.now().getEpochSecond();
        return execute(source, () -> List.of(
                metric(source, "cpu_usage_percent", now, now, 0),
                metric(source, "memory_used_bytes", now, now, 0),
                metric(source, "memory_available_bytes", now, now, 0),
                metric(source, "memory_usage_percent", now, now, 0)));
    }

    public MetricResult history(UUID projectId, UUID id, String metric, Instant start, Instant end, int maxPoints) {
        long seconds = end.getEpochSecond() - start.getEpochSecond();
        if (seconds < 1 || seconds > 7 * 86400 || maxPoints < 2 || maxPoints > 1000
                || end.isAfter(Instant.now().plusSeconds(30)))
            throw new IllegalArgumentException("Период: от 1 секунды до 7 дней, points: 2–1000, конец не в будущем");
        Source source = sources.load(projectId, id);
        MetricCatalog.get(metric, source);
        long step = Math.max(15, (seconds + maxPoints - 2) / (maxPoints - 1));
        return execute(source, () -> metric(source, metric, start.getEpochSecond(), end.getEpochSecond(), step));
    }

    private MetricResult metric(Source source, String id, long start, long end, long step) {
        CacheKey key = new CacheKey(source, id, step == 0 ? 0 : start, step == 0 ? 0 : end, step);
        Cached cached = cache.get(key);
        if (cached != null && System.nanoTime() < cached.expires()) return cached.result();
        var definition = MetricCatalog.get(id, source);
        Health health = health(source, definition.job(), definition.instance());
        Map<String, String> time = step == 0 ? Map.of("time", Long.toString(end)) : Map.of(
                "start", Long.toString(start), "end", Long.toString(end), "step", Long.toString(step));
        JsonNode response = client.query(source, definition.expression(), time);
        List<Series> series = new ArrayList<>();
        for (JsonNode row : response.path("data").path("result")) {
            List<Point> values = new ArrayList<>();
            if (step == 0) values.add(point(row.path("value")));
            else {
                Map<Long, Double> samples = new HashMap<>();
                for (JsonNode sample : row.path("values")) {
                    Point point = point(sample); samples.put(point.timestamp(), point.value());
                }
                for (long t = start; t <= end; t += step) values.add(new Point(t * 1000, samples.get(t * 1000)));
            }
            series.add(new Series(labels(row.path("metric")), List.copyOf(values)));
        }
        boolean hasData = series.stream().flatMap(s -> s.points().stream()).anyMatch(p -> p.value() != null);
        String status = step == 0 && !health.status().equals("UP") ? health.status() : hasData ? "AVAILABLE" : "NO_DATA";
        MetricResult result = new MetricResult(id, definition.unit(), status,
                health, Instant.now(), start * 1000, end * 1000, step, List.copyOf(series), warnings(response));
        synchronized (cache) {
            long now = System.nanoTime();
            cache.entrySet().removeIf(entry -> entry.getValue().expires() < now);
            if (cache.size() >= 32) cache.remove(cache.keySet().iterator().next());
            cache.put(key, new Cached(now + 10_000_000_000L, result));
        }
        return result;
    }

    private static String signedScrapeTime(String selector) {
        return "(timestamp(" + selector + ") and (" + selector + " == 1)) or (-timestamp(" + selector + ") and (" + selector + " == 0))";
    }
    private Health health(Source source, String job, String instance) {
        String selector = "up{job=" + MetricCatalog.quote(job) + ",instance=" + MetricCatalog.quote(instance) + "}";
        JsonNode rows = client.query(source, signedScrapeTime(selector), Map.of()).path("data").path("result");
        if (rows.isEmpty()) return new Health("NO_DATA", null);
        if (rows.size() != 1) throw new MetricsException("AMBIGUOUS_TARGET", "Метки job и instance должны выбирать ровно один target");
        Double value = point(rows.get(0).path("value")).value();
        if (value == null) return new Health("NO_DATA", null);
        long sampledAt = (long) (Math.abs(value) * 1000);
        String status = Instant.now().toEpochMilli() - sampledAt > 60_000 ? "STALE" : value > 0 ? "UP" : "TARGET_DOWN";
        return new Health(status, sampledAt);
    }

    private <T> T execute(Source source, Supplier<T> action) {
        if (!operations.tryAcquire()) throw new MetricsException("BUSY", "Слишком много запросов метрик");
        try {
            T result = action.get();
            sources.status(source, IntegrationStatus.CONNECTED);
            return result;
        } catch (MetricsException | ru.savostov.sre_platform.discovery.DiscoveryException exception) {
            sources.status(source, IntegrationStatus.ERROR);
            synchronized (cache) { cache.keySet().removeIf(key -> key.source().id().equals(source.id())); }
            throw exception;
        } finally { operations.release(); }
    }

    private Point point(JsonNode sample) {
        try {
            if (!sample.isArray() || sample.size() != 2) throw new IllegalArgumentException();
            double seconds = sample.get(0).asDouble();
            double value = Double.parseDouble(sample.get(1).asString());
            return new Point((long) (seconds * 1000), Double.isFinite(value) ? value : null);
        } catch (RuntimeException exception) { throw new MetricsException("INVALID_RESPONSE", "Некорректная точка метрики"); }
    }
    private Map<String, String> labels(JsonNode node) {
        Map<String, String> labels = new TreeMap<>();
        for (var entry : node.properties()) if (!entry.getKey().equals("__name__")) labels.put(entry.getKey(), entry.getValue().asString());
        return Map.copyOf(labels);
    }
    private List<String> warnings(JsonNode response) {
        List<String> result = new ArrayList<>();
        for (JsonNode warning : response.path("warnings")) result.add(warning.asString());
        return List.copyOf(result);
    }
    private record CacheKey(Source source, String metric, long start, long end, long step) {}
    private record Cached(long expires, MetricResult result) {}
    public record Point(long timestamp, Double value) {}
    public record Series(Map<String, String> labels, List<Point> points) {}
    public record Health(String status, Long lastScrapeAt) {}
    public record MetricResult(String metric, String unit, String status, Health target, Instant fetchedAt, long start, long end,
                               long stepSeconds, List<Series> series, List<String> warnings) {}
    public record Target(Map<String, String> labels, boolean up, long sampledAt, boolean stale) {}
    public record TestResult(String status, Instant checkedAt, List<Target> targets, List<String> warnings) {}
}
