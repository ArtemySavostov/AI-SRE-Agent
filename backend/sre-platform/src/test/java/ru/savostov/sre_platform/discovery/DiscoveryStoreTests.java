package ru.savostov.sre_platform.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.repository.ServerRepository;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

class DiscoveryStoreTests {
    private final ServerRepository servers = mock(ServerRepository.class);
    private final ServerSshConnectionRepository connections = mock(ServerSshConnectionRepository.class);
    private final ServerDiscoveryRepository jobs = mock(ServerDiscoveryRepository.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final DiscoveryStore store = new DiscoveryStore(servers, connections, jobs, json);
    private final UUID projectId = UUID.randomUUID();
    private final UUID serverId = UUID.randomUUID();
    private final Server server = new Server();
    @BeforeEach void setup() {
        server.setId(serverId);
        when(servers.lockInProject(serverId, projectId)).thenReturn(Optional.of(server));
        when(servers.findByIdAndProject_Id(serverId, projectId)).thenReturn(Optional.of(server));
    }
    @Test void wrongProjectIsRejectedBeforeSettingsAreRead() {
        UUID other = UUID.randomUUID();
        assertThrows(java.util.NoSuchElementException.class, () -> store.settings(other, serverId));
        verifyNoInteractions(connections);
    }
    @Test void failedCollectionPreservesLastSnapshot() {
        ServerDiscovery job = job(Status.RUNNING);
        Snapshot snapshot = new Snapshot(Instant.now(), new HostInfo("demo", "Ubuntu", "6", "x86", 2, 4096, 1),
                new DockerInfo("24", "demo", "overlay2", 0, 0, 0), List.of());
        job.setSnapshotJson(json.writeValueAsString(snapshot));
        when(jobs.findById(serverId)).thenReturn(Optional.of(job));
        store.fail(projectId, serverId, job.getJobId(), "TIMEOUT", "Timed out");
        JobView result = store.latest(projectId, serverId);
        assertEquals(Status.FAILED, result.status());
        assertEquals(snapshot, result.snapshot());
        assertNotNull(result.completedAt());
        verify(servers).lockInProject(serverId, projectId);
    }
    @Test void busyServerCannotQueueAnotherJobOrChangeConfiguration() {
        when(connections.existsById(serverId)).thenReturn(true);
        when(jobs.findById(serverId)).thenReturn(Optional.of(job(Status.QUEUED)));
        assertEquals("BUSY", assertThrows(DiscoveryException.class, () -> store.queue(projectId, serverId)).getCode());
        var settings = new ConnectionSettings("host",22,"git","demo","SHA256:"+"A".repeat(43));
        assertThrows(DiscoveryException.class, () -> store.configure(projectId, serverId, settings));
        verify(jobs, never()).save(any());
        verify(connections, never()).save(any());
    }
    @Test void staleJobCannotOverwriteNewerResult() {
        ServerDiscovery job = job(Status.SUCCEEDED);
        when(jobs.findById(serverId)).thenReturn(Optional.of(job));
        assertThrows(java.util.NoSuchElementException.class,
                () -> store.fail(projectId, serverId, UUID.randomUUID(), "FAILED", "old worker"));
        assertEquals(Status.SUCCEEDED, job.getStatus());
    }
    @Test void restartMarksInterruptedJobsFailed() {
        ServerDiscovery job = job(Status.RUNNING);
        when(jobs.findByStatusIn(anyList())).thenReturn(List.of(job));
        store.recoverInterruptedJobs();
        assertEquals(Status.FAILED, job.getStatus());
        assertEquals("RESTARTED", job.getErrorCode());
    }
    @Test void fullQueueDoesNotLeaveJobQueuedForever() {
        DiscoveryStore state = mock(DiscoveryStore.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        JobView job = new JobView(UUID.randomUUID(), Status.QUEUED, Instant.now(), null, null, null, null);
        when(state.queue(projectId, serverId)).thenReturn(job);
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        DiscoveryService service = new DiscoveryService(state, mock(DockerDiscoveryProbe.class), mock(SshKeyStore.class), executor);
        assertEquals("QUEUE_FULL", assertThrows(DiscoveryException.class,
                () -> service.discover(projectId, serverId)).getCode());
        verify(state).fail(eq(projectId), eq(serverId), eq(job.jobId()), eq("QUEUE_FULL"), anyString());
    }
    private ServerDiscovery job(Status status) {
        ServerDiscovery job = new ServerDiscovery();
        job.setServerId(serverId); job.setServer(server); job.setJobId(UUID.randomUUID());
        job.setStatus(status); job.setRequestedAt(Instant.now());
        return job;
    }
}
