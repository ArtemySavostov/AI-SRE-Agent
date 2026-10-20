package ru.savostov.sre_platform.agentSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.savostov.sre_platform.SrePlatformApplication;
import ru.savostov.sre_platform.model.project.Project;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.model.user.User;
import ru.savostov.sre_platform.repository.*;
import ru.savostov.sre_platform.service.agentSession.AgentSessionService;
import ru.savostov.sre_platform.service.agentSession.execution.AgentSessionRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.*;

class AgentSessionRestartTests {
    @TempDir Path directory;

    @Test void sessionsAndEventsSurviveContextRestartAndInterruptedWorkIsFailed() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(SrePlatformApplication.class)
                .withBean("testRunner", AgentSessionRunner.class, () -> id -> { }, definition -> definition.setPrimary(true))
                .withPropertyValues("spring.datasource.url=jdbc:h2:file:" + directory.resolve("sessions") + ";MODE=PostgreSQL",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=update");
        AtomicReference<UUID> running = new AtomicReference<>();
        AtomicReference<UUID> pending = new AtomicReference<>();
        AtomicReference<UUID> completed = new AtomicReference<>();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("restart@test.example", "", java.util.List.of()));
        try {
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                User u = new User(); u.setEmail("restart@test.example"); u.setName("test"); u.setPasswordHash("unused"); u.setRole(User.UserRole.ADMIN);
                context.getBean(UserRepository.class).save(u);
                Project p = new Project(); p.setName("restart"); p.setDescription("restart test"); context.getBean(ProjectRepository.class).save(p);
                ManagedService m = new ManagedService(); m.setProject(p); m.setName("restart"); context.getBean(ManagedServiceRepository.class).save(m);
                var service = context.getBean(AgentSessionService.class);
                running.set(service.create(m.getId()).id()); service.start(running.get());
                pending.set(service.create(m.getId()).id());
                completed.set(service.create(m.getId()).id()); service.start(completed.get()); service.complete(completed.get());
            });
            runner.run(context -> {
                assertNull(context.getStartupFailure());
                var service = context.getBean(AgentSessionService.class);
                assertEquals(RUNNING, service.get(running.get()).status());
                // ContextRunner has no SpringApplication lifecycle; deliver the normal boot ready event explicitly.
                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(SrePlatformApplication.class), new String[0],
                        context.getSourceApplicationContext(), Duration.ZERO));
                assertEquals(FAILED, service.get(running.get()).status());
                assertNotNull(service.get(running.get()).finishedAt());
                assertEquals(3, service.events(running.get()).size());
                assertEquals(PENDING, service.get(pending.get()).status());
                assertEquals(COMPLETED, service.get(completed.get()).status());
                assertEquals(3, service.events(completed.get()).size());
            });
        } finally { SecurityContextHolder.clearContext(); }
    }
}
