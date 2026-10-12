package ru.savostov.sre_platform.transport;

import java.net.URI;

public record RemoteEndpoint(String host, int port) {
    public static RemoteEndpoint parse(String value) {
        try {
            URI uri = URI.create(value.strip());
            if (!"http".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            if (!uri.getHost().equals("127.0.0.1") && !uri.getHost().equals("localhost")) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            if (port < 1 || port > 65535) { throw new IllegalArgumentException(); }
            return new RemoteEndpoint("127.0.0.1", port);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Укажите http://127.0.0.1:порт без пути, credentials и query-параметров");
        }
    }
}
