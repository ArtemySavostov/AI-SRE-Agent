package ru.savostov.sre_platform.metrics;

import jakarta.annotation.PreDestroy;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;
import org.springframework.stereotype.Component;
import ru.savostov.sre_platform.discovery.DiscoveryModels.ConnectionSettings;
import ru.savostov.sre_platform.discovery.SshKeyStore;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SshTunnelManager {
    private final SshKeyStore keys;
    private final Map<UUID, Tunnel> tunnels = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("metrics-tunnel-cleaner").factory());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("metrics-ssh-deadlines").factory());

    public SshTunnelManager(SshKeyStore keys) {
        this.keys = keys;
        cleaner.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
    }

    public synchronized int port(UUID id, ConnectionSettings settings, PrometheusEndpoint endpoint) {
        Tunnel current = tunnels.get(id);
        if (current != null && current.settings.equals(settings) && current.endpoint.equals(endpoint)
                && current.ssh.isConnected() && current.thread.isAlive()) {
            current.used = System.nanoTime();
            return current.socket.getLocalPort();
        }
        invalidate(id);
        if (retryAfter.getOrDefault(id, 0L) > System.nanoTime()) {
            throw new MetricsException("SSH_RETRY_LATER", "Повторное подключение SSH будет доступно через несколько секунд");
        }
        if (tunnels.size() >= 16) { throw new MetricsException("BUSY", "Достигнут лимит SSH-туннелей"); }
        var key = keys.resolve(settings.credentialId());
        SSHClient ssh = new SSHClient();
        ServerSocket socket = null;
        AtomicBoolean rejected = new AtomicBoolean();
        String stage = "CONNECT";
        ScheduledFuture<?> deadline = deadlines.schedule(() -> {
            try { ssh.close(); } catch (IOException ignored) { }
        }, 30, TimeUnit.SECONDS);
        try {
            ssh.setConnectTimeout(10_000);
            ssh.setTimeout(15_000);
            var verifier = FingerprintVerifier.getInstance(settings.hostKeyFingerprint());
            ssh.addHostKeyVerifier(new net.schmizz.sshj.transport.verification.HostKeyVerifier() {
                @Override public boolean verify(String host, int port, java.security.PublicKey publicKey) {
                    boolean valid = verifier.verify(host, port, publicKey);
                    if (!valid) rejected.set(true);
                    return valid;
                }
                @Override public List<String> findExistingAlgorithms(String host, int port) { return List.of(); }
            });
            ssh.connect(settings.host(), settings.port());
            stage = "AUTH";
            ssh.authPublickey(settings.username(), ssh.loadKeys(key.toString()));
            ssh.getConnection().getKeepAlive().setKeepAliveInterval(15);
            socket = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
            LocalPortForwarder forwarder = ssh.newLocalPortForwarder(
                    new Parameters("127.0.0.1", socket.getLocalPort(), endpoint.host(), endpoint.port()), socket);
            Thread thread = Thread.ofVirtual().name("prometheus-tunnel-" + id).unstarted(() -> {
                try { forwarder.listen(); } catch (IOException ignored) { /* Recreated on the next request. */ }
            });
            Tunnel tunnel = new Tunnel(ssh, socket, forwarder, thread, settings, endpoint);
            thread.start();
            tunnels.put(id, tunnel);
            retryAfter.remove(id);
            return socket.getLocalPort();
        } catch (IOException | RuntimeException exception) {
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
            try { ssh.close(); } catch (IOException ignored) { }
            retryAfter.put(id, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            if (rejected.get()) { throw new MetricsException("HOST_KEY_REJECTED", "Отпечаток SSH-ключа сервера изменился"); }
            throw new MetricsException("AUTH".equals(stage) ? "SSH_AUTH_FAILED" : "SSH_UNAVAILABLE",
                    "Не удалось открыть SSH-туннель: проверьте подключение, ключ и настройки SSH");
        } finally {
            deadline.cancel(false);
        }
    }

    public synchronized void failed(UUID id) {
        invalidate(id);
        retryAfter.put(id, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
    }

    public synchronized void invalidate(UUID id) {
        Tunnel tunnel = tunnels.remove(id);
        if (tunnel != null) {
            try { tunnel.forwarder.close(); } catch (IOException ignored) { }
            try { tunnel.socket.close(); } catch (IOException ignored) { }
            try { tunnel.ssh.close(); } catch (IOException ignored) { }
            tunnel.thread.interrupt();
        }
    }

    private synchronized void expire() {
        long now = System.nanoTime();
        for (UUID id : new ArrayList<>(tunnels.keySet())) {
            if (now - tunnels.get(id).used > TimeUnit.MINUTES.toNanos(5)) invalidate(id);
        }
        retryAfter.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    @PreDestroy
    public synchronized void close() {
        cleaner.shutdownNow();
        deadlines.shutdownNow();
        for (UUID id : new ArrayList<>(tunnels.keySet())) invalidate(id);
    }

    private static final class Tunnel {
        final SSHClient ssh;
        final ServerSocket socket;
        final LocalPortForwarder forwarder;
        final Thread thread;
        final ConnectionSettings settings;
        final PrometheusEndpoint endpoint;
        long used = System.nanoTime();
        Tunnel(SSHClient ssh, ServerSocket socket, LocalPortForwarder forwarder, Thread thread,
               ConnectionSettings settings, PrometheusEndpoint endpoint) {
            this.ssh = ssh; this.socket = socket; this.forwarder = forwarder; this.thread = thread;
            this.settings = settings; this.endpoint = endpoint;
        }
    }
}
