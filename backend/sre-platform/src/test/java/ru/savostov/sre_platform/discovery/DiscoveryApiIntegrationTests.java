package ru.savostov.sre_platform.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.repository.ProjectRepository;
import ru.savostov.sre_platform.repository.ServerRepository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.savostov.sre_platform.discovery.DiscoveryModels.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:discovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=discovery-test-only", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop" })
@AutoConfigureMockMvc
class DiscoveryApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired ServerRepository servers;
    @Autowired DiscoveryStore store;
    @Autowired JsonMapper json;
    @MockitoBean DockerDiscoveryProbe probe;
    @MockitoBean SshKeyStore keys;
    private UUID projectId;
    private UUID serverId;
    private String base;
    private final ConnectionSettings settings = new ConnectionSettings("localhost", 2522, "git", "demo_ed25519",
            "SHA256:" + "A".repeat(43));

    @BeforeEach
    void setup() {
        Project project = new Project();
        project.setName("Discovery test"); project.setDescription("isolated test");
        projectId = projects.save(project).getId();
        Server server = new Server();
        server.setProject(project); server.setName("demo"); server.setHostname("demo");
        server.setIpAddress("127.0.0.1"); server.setOs("Linux");
        serverId = servers.save(server).getId();
        base = "/api/project/" + projectId + "/server/" + serverId;
        when(keys.resolve(anyString())).thenReturn(Path.of("test-key"));
    }
    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(base + "/ssh")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser
    void requiresCsrfAndValidSettingsAndServerScope() throws Exception {
        mvc.perform(put(base + "/ssh").contentType("application/json").content(json.writeValueAsString(settings)))
                .andExpect(status().isForbidden());
        mvc.perform(put(base + "/ssh").with(csrf()).contentType("application/json")
                .content("{\"host\":\"localhost\",\"port\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(base.replace(projectId.toString(), UUID.randomUUID().toString()) + "/ssh")
                .with(csrf()).contentType("application/json").content(json.writeValueAsString(settings)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(keys);
    }
    @Test @WithMockUser
    void persistsSnapshotAndKeepsItOnFailureAndRejectsConcurrentRuns() throws Exception {
        mvc.perform(put(base + "/ssh").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(settings))).andExpect(status().isOk());
        mvc.perform(get(base + "/ssh")).andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value("demo_ed25519"));
        Snapshot snapshot = new Snapshot(Instant.now(), new HostInfo("demo", "Ubuntu", "6.8", "x86_64", 2, 4096, 12),
                new DockerInfo("24.0.2", "demo", "overlay2", 0, 0, 0), List.of());
        CountDownLatch release = new CountDownLatch(1);
        when(probe.discover(any())).thenAnswer(invocation -> {
            if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Test timeout"); }
            return snapshot;
        });
        try {
            mvc.perform(post(base + "/discovery").with(csrf())).andExpect(status().isAccepted());
            mvc.perform(post(base + "/discovery").with(csrf())).andExpect(status().isConflict());
            mvc.perform(put(base + "/ssh").with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(settings))).andExpect(status().isConflict());
        } finally { release.countDown(); }
        await(Status.SUCCEEDED);
        mvc.perform(get(base + "/discovery")).andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.host.hostname").value("demo"));
        when(probe.discover(any())).thenThrow(new DiscoveryException("SSH_FAILED", "Unavailable"));
        mvc.perform(post(base + "/discovery").with(csrf())).andExpect(status().isAccepted());
        await(Status.FAILED);
        JobView failed = store.latest(projectId, serverId);
        assertEquals("SSH_FAILED", failed.errorCode());
        assertEquals("demo", failed.snapshot().host().hostname());
        assertEquals(snapshot.collectedAt(), failed.snapshot().collectedAt());
    }
    @Test @WithMockUser
    void recoversInterruptedJobsWithoutLosingSnapshot() {
        store.configure(projectId, serverId, settings);
        var job = store.queue(projectId, serverId);
        store.recoverInterruptedJobs();
        var restored = store.latest(projectId, serverId);
        assertEquals(job.jobId(), restored.jobId());
        assertEquals(Status.FAILED, restored.status());
        assertEquals("RESTARTED", restored.errorCode());
    }
    private void await(Status expected) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            if (store.latest(projectId, serverId).status() == expected) { return; }
            Thread.sleep(50);
        }
        fail("Discovery did not reach " + expected);
    }
}
