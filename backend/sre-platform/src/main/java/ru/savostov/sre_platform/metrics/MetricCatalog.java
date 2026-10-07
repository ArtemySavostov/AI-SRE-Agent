package ru.savostov.sre_platform.metrics;

import java.util.*;
import ru.savostov.sre_platform.metrics.PrometheusSourceStore.Source;

public final class MetricCatalog {
    private MetricCatalog() {}
    public record Definition(String id, String unit, String expression, String job, String instance) {}
    public static Definition get(String id, Source s) {
        String n = "job=" + quote(s.nodeJob()) + ",instance=" + quote(s.nodeInstance());
        String c = "job=" + quote(s.containerJob()) + ",instance=" + quote(s.containerInstance());
        String disk = n + ",device!~\"loop.*|ram.*\"";
        String fs = n + ",fstype!~\"tmpfs|devtmpfs|overlay|squashfs|nsfs\",mountpoint!~\"/run.*|/var/lib/docker.*\"";
        String net = n + ",device!~\"lo|veth.*|docker.*|br-.*\"";
        String containers = c + ",image!=\"\"";
        String unit;
        String expr;
        switch (id) {
            case "cpu_usage_percent" -> { unit = "percent"; expr = "100 * (1 - avg(rate(node_cpu_seconds_total{" + n + ",mode=\"idle\"}[2m])))"; }
            case "memory_used_bytes" -> { unit = "bytes"; expr = "node_memory_MemTotal_bytes{" + n + "} - node_memory_MemAvailable_bytes{" + n + "}"; }
            case "memory_available_bytes" -> { unit = "bytes"; expr = "node_memory_MemAvailable_bytes{" + n + "}"; }
            case "memory_usage_percent" -> { unit = "percent"; expr = "100 * (1 - node_memory_MemAvailable_bytes{" + n + "} / node_memory_MemTotal_bytes{" + n + "})"; }
            case "filesystem_usage_percent" -> { unit = "percent"; expr = "100 * (1 - node_filesystem_avail_bytes{" + fs + "} / node_filesystem_size_bytes{" + fs + "})"; }
            case "network_receive_bytes_per_second" -> { unit = "bytes/s"; expr = "rate(node_network_receive_bytes_total{" + net + "}[2m])"; }
            case "network_transmit_bytes_per_second" -> { unit = "bytes/s"; expr = "rate(node_network_transmit_bytes_total{" + net + "}[2m])"; }
            case "disk_read_bytes_per_second" -> { unit = "bytes/s"; expr = "rate(node_disk_read_bytes_total{" + disk + "}[2m])"; }
            case "disk_write_bytes_per_second" -> { unit = "bytes/s"; expr = "rate(node_disk_written_bytes_total{" + disk + "}[2m])"; }
            case "uptime_seconds" -> { unit = "seconds"; expr = "time() - node_boot_time_seconds{" + n + "}"; }
            case "container_cpu_cores" -> { unit = "cores"; expr = "sum by (id,name,container_label_com_docker_compose_service) (rate(container_cpu_usage_seconds_total{" + containers + "}[2m]))"; }
            case "container_memory_working_set_bytes" -> { unit = "bytes"; expr = "container_memory_working_set_bytes{" + containers + "}"; }
            default -> throw new IllegalArgumentException("Неизвестная метрика: " + id);
        }
        boolean container = id.startsWith("container_");
        String selector = container ? c : n;
        // Suppress stale cached exporter values and rates spanning a failed scrape.
        expr = "(" + expr + ") and on() (up{" + selector + "} == 1) and on() ((time() - timestamp(up{" + selector + "})) < 60)";
        return new Definition(id, unit, expr, container ? s.containerJob() : s.nodeJob(), container ? s.containerInstance() : s.nodeInstance());
    }
    public static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
