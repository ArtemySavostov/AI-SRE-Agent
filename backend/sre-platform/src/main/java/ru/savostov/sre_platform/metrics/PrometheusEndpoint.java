package ru.savostov.sre_platform.metrics;

import ru.savostov.sre_platform.transport.RemoteEndpoint;

/** Compatibility facade for the existing metrics API. */
public record PrometheusEndpoint(String host, int port) {
    public static PrometheusEndpoint parse(String value) {
        var endpoint = RemoteEndpoint.parse(value);
        return new PrometheusEndpoint(endpoint.host(), endpoint.port());
    }
}
