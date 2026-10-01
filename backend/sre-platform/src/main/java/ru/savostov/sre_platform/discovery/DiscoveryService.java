package ru.savostov.sre_platform.discovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@Service
public class DiscoveryService {
    private final DiscoveryStore store;
    private final DockerDiscoveryProbe probe;
    private final SshKeyStore keys;
    private final TaskExecutor executor;
    public DiscoveryService(DiscoveryStore store, DockerDiscoveryProbe probe, SshKeyStore keys,
                            @Qualifier("discoveryExecutor") TaskExecutor executor) {
        this.store = store;
        this.probe = probe;
        this.keys = keys;
        this.executor = executor;
    }
    public ConnectionSettings configure(UUID projectId, UUID serverId, ConnectionSettings settings) {
        // Scope check comes before accessing the local key store.
        store.requireServerInProject(projectId, serverId);
        keys.resolve(settings.credentialId());
        return store.configure(projectId, serverId, settings);
    }
    public ConnectionTest test(UUID projectId, UUID serverId) {
        return probe.test(store.settings(projectId, serverId));
    }
    public JobView discover(UUID projectId, UUID serverId) {
        JobView job = store.queue(projectId, serverId);
        try {
            executor.execute(() -> execute(projectId, serverId, job.jobId()));
        } catch (RejectedExecutionException exception) {
            store.fail(projectId, serverId, job.jobId(), "QUEUE_FULL", "Очередь discovery заполнена, повторите позже");
            throw new DiscoveryException("QUEUE_FULL", "Очередь discovery заполнена, повторите позже");
        }
        return job;
    }
    private void execute(UUID projectId, UUID serverId, UUID jobId) {
        try {
            ConnectionSettings settings = store.start(projectId, serverId, jobId);
            Snapshot snapshot = probe.discover(settings);
            store.succeed(projectId, serverId, jobId, snapshot);
        } catch (DiscoveryException exception) {
            store.fail(projectId, serverId, jobId, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            store.fail(projectId, serverId, jobId, "DISCOVERY_FAILED", "Не удалось завершить discovery");
        }
    }
}
