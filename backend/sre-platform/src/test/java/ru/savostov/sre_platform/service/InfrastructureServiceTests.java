package ru.savostov.sre_platform.service;

import org.junit.jupiter.api.Test;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.server.Server;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.repository.ManagedServiceRepository;
import ru.savostov.sre_platform.repository.ProjectRepository;
import ru.savostov.sre_platform.repository.ServerRepository;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfrastructureServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ServerRepository servers = mock(ServerRepository.class);
    private final ManagedServiceRepository services = mock(ManagedServiceRepository.class);
    private final ServerService serverService = new ServerService(servers, projects);
    private final ManagedServiceService managedService = new ManagedServiceService(services, servers);
    private final UUID projectId = UUID.randomUUID();
    private final UUID serverId = UUID.randomUUID();

    @Test
    void serverIsAttachedToProjectAndStartsInactive() {
        Project project = new Project();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(servers.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Server result = serverService.createServer(projectId, " host ", " host.local ", " 127.0.0.1 ", " Linux ");
        assertSame(project, result.getProject());
        assertEquals("host", result.getName());
        assertEquals(Server.ServerStatus.INACTIVE, result.getStatus());
    }

    @Test
    void missingProjectPreventsCreationAndListing() {
        assertThrows(NoSuchElementException.class,
                () -> serverService.createServer(projectId, "host", "host.local", "127.0.0.1", "Linux"));
        assertThrows(NoSuchElementException.class, () -> serverService.getServers(projectId));
        verifyNoInteractions(servers);
    }

    @Test
    void invalidServerFieldsAreNotSaved() {
        when(projects.findById(projectId)).thenReturn(Optional.of(new Project()));
        assertThrows(IllegalArgumentException.class,
                () -> serverService.createServer(projectId, "host", " ", "127.0.0.1", "Linux"));
        assertThrows(IllegalArgumentException.class,
                () -> serverService.createServer(projectId, "host", "host.local", "127.0.0.1", "x".repeat(101)));
        verifyNoInteractions(servers);
    }

    @Test
    void serviceInheritsProjectFromServerAndStartsUnknown() {
        Project project = new Project();
        Server server = new Server();
        server.setProject(project);
        when(servers.findByIdAndProject_Id(serverId, projectId)).thenReturn(Optional.of(server));
        when(services.save(any(ManagedService.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ManagedService result = managedService.createService(projectId, serverId, " api ", null, " ");
        assertSame(server, result.getServer());
        assertSame(project, result.getProject());
        assertEquals("api", result.getName());
        assertNull(result.getHealthcheckUrl());
        assertEquals(ManagedService.ServiceStatus.UNKNOWN, result.getStatus());
    }

    @Test
    void serverOutsideProjectCannotBeUsedForCreationOrListing() {
        when(servers.findByIdAndProject_Id(serverId, projectId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> managedService.createService(projectId, serverId, "api", null, null));
        assertThrows(NoSuchElementException.class, () -> managedService.getServices(projectId, serverId));
        verifyNoInteractions(services);
    }

    @Test
    void invalidServiceIsNotSaved() {
        when(servers.findByIdAndProject_Id(serverId, projectId)).thenReturn(Optional.of(new Server()));
        assertThrows(IllegalArgumentException.class,
                () -> managedService.createService(projectId, serverId, " ", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> managedService.createService(projectId, serverId, "api", null, "x".repeat(256)));
        verifyNoInteractions(services);
    }
}
