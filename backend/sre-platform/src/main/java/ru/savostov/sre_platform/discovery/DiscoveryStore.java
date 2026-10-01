package ru.savostov.sre_platform.discovery;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.repository.ServerRepository;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@Service
@Transactional
public class DiscoveryStore {
    private final ServerRepository servers;
    private final ServerSshConnectionRepository connections;
    private final ServerDiscoveryRepository discoveries;
    private final JsonMapper json;
    public DiscoveryStore(ServerRepository servers, ServerSshConnectionRepository connections,
                          ServerDiscoveryRepository discoveries, JsonMapper json) {
        this.servers = servers;
        this.connections = connections;
        this.discoveries = discoveries;
        this.json = json;
    }
    public ConnectionSettings configure(UUID projectId, UUID serverId, ConnectionSettings settings) {
        Server server = lock(projectId, serverId);
        discoveries.findById(serverId).ifPresent(this::ensureIdle);
        ServerSshConnection connection = connections.findById(serverId).orElseGet(ServerSshConnection::new);
        connection.setServer(server);
        connection.setHost(settings.host());
        connection.setPort(settings.port());
        connection.setUsername(settings.username());
        connection.setCredentialId(settings.credentialId());
        connection.setHostKeyFingerprint(settings.hostKeyFingerprint());
        return connections.save(connection).settings();
    }
    @Transactional(readOnly = true)
    public ConnectionSettings settings(UUID projectId, UUID serverId) {
        requireServer(projectId, serverId);
        return connections.findById(serverId).orElseThrow(() ->
                new NoSuchElementException("SSH-подключение ещё не настроено")).settings();
    }
    public JobView queue(UUID projectId, UUID serverId) {
        Server server = lock(projectId, serverId);
        if (!connections.existsById(serverId)) {
            throw new NoSuchElementException("Сначала настройте SSH-подключение");
        }
        ServerDiscovery job = discoveries.findById(serverId).orElseGet(ServerDiscovery::new);
        ensureIdle(job);
        job.setServer(server);
        job.setJobId(UUID.randomUUID());
        job.setStatus(Status.QUEUED);
        job.setRequestedAt(Instant.now());
        job.setCompletedAt(null);
        job.setErrorCode(null);
        job.setError(null);
        return view(discoveries.save(job));
    }
    public ConnectionSettings start(UUID projectId, UUID serverId, UUID jobId) {
        lock(projectId, serverId);
        ServerDiscovery job = current(serverId, jobId);
        if (job.getStatus() != Status.QUEUED) { throw new IllegalStateException("Job is not queued"); }
        job.setStatus(Status.RUNNING);
        return connections.findById(serverId).orElseThrow().settings();
    }
    public void succeed(UUID projectId, UUID serverId, UUID jobId, Snapshot snapshot) {
        lock(projectId, serverId);
        ServerDiscovery job = current(serverId, jobId);
        job.setSnapshotJson(json.writeValueAsString(snapshot));
        job.setStatus(Status.SUCCEEDED);
        job.setCompletedAt(Instant.now());
    }
    public void fail(UUID projectId, UUID serverId, UUID jobId, String code, String message) {
        lock(projectId, serverId);
        ServerDiscovery job = current(serverId, jobId);
        job.setStatus(Status.FAILED);
        job.setCompletedAt(Instant.now());
        job.setErrorCode(code);
        job.setError(message);
        // Keep the last successful snapshot on failed or partial collection.
    }
    @Transactional(readOnly = true)
    public JobView latest(UUID projectId, UUID serverId) {
        requireServer(projectId, serverId);
        return view(discoveries.findById(serverId).orElseThrow(() ->
                new NoSuchElementException("Discovery ещё не запускался")));
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        for (ServerDiscovery job : discoveries.findByStatusIn(List.of(Status.QUEUED, Status.RUNNING))) {
            job.setStatus(Status.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorCode("RESTARTED");
            job.setError("Backend перезапущен во время discovery; запустите сбор повторно");
        }
    }
    private Server lock(UUID projectId, UUID serverId) {
        return servers.lockInProject(serverId, projectId).orElseThrow(() ->
                new NoSuchElementException("Сервер не найден в указанном проекте"));
    }
    @Transactional(readOnly = true)
    public void requireServerInProject(UUID projectId, UUID serverId) {
        requireServer(projectId, serverId);
    }
    private void requireServer(UUID projectId, UUID serverId) {
        servers.findByIdAndProject_Id(serverId, projectId).orElseThrow(() ->
                new NoSuchElementException("Сервер не найден в указанном проекте"));
    }
    private ServerDiscovery current(UUID serverId, UUID jobId) {
        return discoveries.findById(serverId).filter(job -> jobId.equals(job.getJobId()))
                .orElseThrow(() -> new NoSuchElementException("Задача discovery не найдена"));
    }
    private void ensureIdle(ServerDiscovery job) {
        if (job.getStatus() == Status.QUEUED || job.getStatus() == Status.RUNNING) {
            throw new DiscoveryException("BUSY", "Discovery этого сервера уже выполняется");
        }
    }
    private JobView view(ServerDiscovery job) {
        Snapshot snapshot = job.getSnapshotJson() == null ? null : json.readValue(job.getSnapshotJson(), Snapshot.class);
        return new JobView(job.getJobId(), job.getStatus(), job.getRequestedAt(), job.getCompletedAt(),
                job.getErrorCode(), job.getError(), snapshot);
    }
}
