package ru.savostov.sre_platform.discovery;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SshTransport {
    private final SshKeyStore keys;
    private final ScheduledExecutorService deadlines;
    private final Semaphore connections = new Semaphore(4);

    public SshTransport(SshKeyStore keys, ScheduledExecutorService sshDeadlines) {
        this.keys = keys;
        this.deadlines = sshDeadlines;
    }

    Map<RemoteCommand, String> collect(DiscoveryModels.ConnectionSettings settings, List<RemoteCommand> commands) {
        Path key = keys.resolve(settings.credentialId());
        if (!connections.tryAcquire()) {
            throw new DiscoveryException("BUSY", "Все SSH-подключения заняты, повторите позже");
        }
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicBoolean keyRejected = new AtomicBoolean();
        String stage = "CONNECT";
        try (SSHClient ssh = new SSHClient()) {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            ScheduledFuture<?> timeout = deadlines.schedule(() -> {
                timedOut.set(true);
                try { ssh.close(); } catch (IOException ignored) { }
            }, 60, TimeUnit.SECONDS);
            try {
                ssh.setConnectTimeout(10_000);
                ssh.setTimeout(15_000);
                var verifier = FingerprintVerifier.getInstance(settings.hostKeyFingerprint());
                ssh.addHostKeyVerifier(new net.schmizz.sshj.transport.verification.HostKeyVerifier() {
                    @Override
                    public boolean verify(String host, int port, java.security.PublicKey publicKey) {
                        boolean valid = verifier.verify(host, port, publicKey);
                        if (!valid) { keyRejected.set(true); }
                        return valid;
                    }
                    @Override
                    public List<String> findExistingAlgorithms(String host, int port) { return List.of(); }
                });
                ssh.connect(settings.host(), settings.port());
                stage = "AUTH";
                ssh.authPublickey(settings.username(), ssh.loadKeys(key.toString()));
                stage = "COMMAND";
                Map<RemoteCommand, String> result = new EnumMap<>(RemoteCommand.class);
                for (RemoteCommand command : commands) {
                    result.put(command, execute(ssh, command, end));
                }
                return result;
            } finally {
                timeout.cancel(false);
            }
        } catch (DiscoveryException exception) {
            throw exception;
        } catch (IOException exception) {
            if (timedOut.get()) {
                throw new DiscoveryException("TIMEOUT", "Превышено время SSH-операции (60 секунд)");
            }
            if (keyRejected.get()) {
                throw new DiscoveryException("HOST_KEY_REJECTED", "Отпечаток SSH-ключа сервера не совпадает");
            }
            if ("AUTH".equals(stage)) {
                throw new DiscoveryException("AUTH_FAILED", "SSH-аутентификация не удалась: проверьте пользователя и ключ");
            }
            throw new DiscoveryException("SSH_FAILED", "Не удалось выполнить SSH-операцию: проверьте адрес, порт и доступность сервера");
        } finally {
            connections.release();
        }
    }

    private String execute(SSHClient ssh, RemoteCommand command, long end) throws IOException {
        try (Session session = ssh.startSession(); Session.Command process = session.exec(command.script)) {
            CompletableFuture<String> stdout = new CompletableFuture<>();
            CompletableFuture<String> stderr = new CompletableFuture<>();
            Thread out = Thread.ofVirtual().start(() -> readInto(process.getInputStream(), 4 * 1024 * 1024, stdout));
            Thread err = Thread.ofVirtual().start(() -> readInto(process.getErrorStream(), 64 * 1024, stderr));
            try {
                String output = stdout.get(remaining(end), TimeUnit.NANOSECONDS);
                stderr.get(remaining(end), TimeUnit.NANOSECONDS);
                process.join(remaining(end), TimeUnit.NANOSECONDS);
                if (process.getExitStatus() == null) {
                    throw new TimeoutException();
                }
                if (process.getExitStatus() != 0) {
                    throw new DiscoveryException("REMOTE_COMMAND_FAILED", command == RemoteCommand.HOST
                            ? "Не удалось прочитать сведения Linux-хоста"
                            : "Не удалось прочитать Docker: проверьте установку и доступ пользователя к Docker socket; лимит 1000 контейнеров");
                }
                return output;
            } catch (TimeoutException exception) {
                throw new DiscoveryException("TIMEOUT", "Превышено время SSH-операции (60 секунд)");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DiscoveryException("INTERRUPTED", "SSH-операция прервана");
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof DiscoveryException failure) { throw failure; }
                throw new IOException("Failed reading SSH response", exception);
            } finally {
                out.interrupt();
                err.interrupt();
            }
        }
    }

    private long remaining(long end) throws TimeoutException {
        long remaining = end - System.nanoTime();
        if (remaining <= 0) { throw new TimeoutException(); }
        return remaining;
    }

    private void readInto(InputStream stream, int limit, CompletableFuture<String> result) {
        try {
            result.complete(readLimited(stream, limit));
        } catch (Exception exception) {
            result.completeExceptionally(exception);
        }
    }

    static String readLimited(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count > limit - output.size()) {
                throw new DiscoveryException("OUTPUT_LIMIT", "SSH-ответ превышает допустимый размер");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
