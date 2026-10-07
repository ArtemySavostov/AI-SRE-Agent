package ru.savostov.sre_platform.metrics;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import java.util.stream.Collectors;
import ru.savostov.sre_platform.metrics.PrometheusSourceStore.Source;

@Component
public class PrometheusClient {
    private final SshTunnelManager tunnels;
    private final JsonMapper json;
    private final Semaphore requests = new Semaphore(8);
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("prometheus-deadlines").factory());
    public PrometheusClient(SshTunnelManager tunnels, JsonMapper json) { this.tunnels = tunnels; this.json = json; }
    public void ready(Source source) { request(source, "/-/ready", Map.of(), false); }
    public JsonNode query(Source source, String expression, Map<String, String> time) {
        var params = new java.util.LinkedHashMap<>(time);
        params.put("query", expression); params.put("timeout", "8s"); params.put("limit", "65");
        return request(source, time.containsKey("start") ? "/api/v1/query_range" : "/api/v1/query", params, true);
    }
    private JsonNode request(Source source, String path, Map<String, String> params, boolean parse) {
        if (!requests.tryAcquire()) throw new MetricsException("BUSY", "Слишком много запросов метрик");
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        try {
            int port = tunnels.port(source.id(), source.ssh(), source.endpoint());
            String query = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path + "?" + query).toURL().openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(5_000); connection.setReadTimeout(12_000);
            connection.setInstanceFollowRedirects(false);
            HttpURLConnection active = connection;
            deadline = deadlines.schedule(active::disconnect, 15, TimeUnit.SECONDS);
            if (connection.getResponseCode() != 200) throw new MetricsException("PROMETHEUS_HTTP_ERROR", "Prometheus вернул HTTP " + connection.getResponseCode());
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) { bytes = input.readNBytes(8 * 1024 * 1024 + 1); }
            if (bytes.length > 8 * 1024 * 1024) throw new MetricsException("RESPONSE_TOO_LARGE", "Ответ Prometheus превышает 8 MiB");
            if (!parse) return null;
            JsonNode result;
            try { result = json.readTree(bytes); }
            catch (RuntimeException exception) { throw new MetricsException("INVALID_RESPONSE", "Некорректный JSON от Prometheus"); }
            if (result == null || !"success".equals(result.path("status").asString("")) || !result.path("data").path("result").isArray())
                throw new MetricsException("INVALID_RESPONSE", "Prometheus не вернул результат запроса");
            String expectedType = params.containsKey("start") ? "matrix" : "vector";
            if (!expectedType.equals(result.path("data").path("resultType").asString("")))
                throw new MetricsException("INVALID_RESPONSE", "Неожиданный тип результата Prometheus");
            if (result.path("data").path("result").size() > 64)
                throw new MetricsException("TOO_MANY_SERIES", "Запрос вернул более 64 рядов; уточните target");
            return result;
        } catch (IOException exception) {
            tunnels.failed(source.id());
            throw new MetricsException("PROMETHEUS_UNREACHABLE", "Prometheus недоступен через SSH: проверьте порт, TCP forwarding и состояние сервиса");
        } finally {
            if (deadline != null) deadline.cancel(false);
            if (connection != null) connection.disconnect();
            requests.release();
        }
    }
    @PreDestroy
    public void close() { deadlines.shutdownNow(); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
