package ru.savostov.sre_platform.service.agentSession;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.savostov.sre_platform.metrics.ProjectMetricsAccess;
import ru.savostov.sre_platform.model.agentSession.*;
import ru.savostov.sre_platform.model.agentEvent.*;
import ru.savostov.sre_platform.model.service.ManagedService;
import ru.savostov.sre_platform.repository.*;
import java.util.*;
import static ru.savostov.sre_platform.model.agentSession.AgentSessionStatus.*;
import static ru.savostov.sre_platform.model.agentEvent.AgentEventType.*;
import static ru.savostov.sre_platform.service.agentSession.AgentSessionDtos.*;

@Service
@Transactional(readOnly = true)
public class AgentSessionService {
    private final AgentSessionRepository sessions;
    private final AgentEventRepository events;
    private final ManagedServiceRepository services;
    private final ProjectMetricsAccess access;
    private final ApplicationEventPublisher publisher;

    public AgentSessionService(AgentSessionRepository sessions, AgentEventRepository events,
            ManagedServiceRepository services, ProjectMetricsAccess access, ApplicationEventPublisher publisher) {
        this.sessions = sessions; this.events = events; this.services = services; this.access = access; this.publisher = publisher;
    }

    @Transactional
    public SessionResponse create(UUID serviceId) {
        AgentSession session = sessions.saveAndFlush(new AgentSession(authorizedService(serviceId)));
        append(session, SESSION_CREATED, "Сессия создана. Исполнитель: mock; реальная диагностика не выполняется.");
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse start(UUID id) {
        AgentSession session = authorizedSession(id, true);
        if (session.getStatus() != PENDING) throw new IllegalStateException("Запуск возможен только из PENDING");
        transition(session, RUNNING, SESSION_STARTED, "Mock-исполнение запущено", null, null);
        publisher.publishEvent(new StartRequested(id));
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse cancel(UUID id) {
        AgentSession session = authorizedSession(id, true);
        transition(session, CANCELLED, SESSION_CANCELLED, "Сессия отменена пользователем", null, null);
        return SessionResponse.from(session);
    }

    public SessionResponse get(UUID id) { return SessionResponse.from(authorizedSession(id, false)); }
    public List<SessionResponse> history(UUID serviceId) {
        authorizedService(serviceId);
        return sessions.findByService_IdOrderByCreatedAtDescIdDesc(serviceId).stream().map(SessionResponse::from).toList();
    }
    public List<EventResponse> events(UUID id) {
        authorizedSession(id, false);
        return events.findByAgentSession_IdOrderBySequenceNumberAsc(id).stream().map(EventResponse::from).toList();
    }

    // Internal worker operations: every stage uses a short, independent transaction and the same row lock as the API.
    @Transactional
    public boolean recordMockStage(UUID id, String message) {
        AgentSession session = locked(id);
        if (session.getStatus() != RUNNING) return false;
        append(session, DIAGNOSTIC_STARTED, message);
        return true;
    }

    @Transactional
    public boolean complete(UUID id) {
        AgentSession session = locked(id);
        if (session.getStatus() != RUNNING) return false;
        transition(session, COMPLETED, SESSION_COMPLETED, "Mock-исполнение завершено",
                "Тестовый запуск завершён. Метрики и логи не собирались; AI-анализ и восстановление не выполнялись.", null);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, String safeMessage) {
        AgentSession session = locked(id);
        if (session.getStatus() != RUNNING && session.getStatus() != WAITING_APPROVAL) return;
        transition(session, FAILED, SESSION_FAILED, safeMessage, null, safeMessage);
    }

    private ManagedService authorizedService(UUID id) {
        ManagedService service = services.findById(id).orElseThrow(() -> new NoSuchElementException("Сервис не найден"));
        access.require(service.getProject().getId());
        return service;
    }
    private AgentSession authorizedSession(UUID id, boolean lock) {
        AgentSession session = lock ? locked(id) : sessions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Сессия не найдена"));
        access.require(session.getService().getProject().getId());
        return session;
    }
    private AgentSession locked(UUID id) {
        return sessions.lockById(id).orElseThrow(() -> new NoSuchElementException("Сессия не найдена"));
    }
    private void transition(AgentSession s, AgentSessionStatus status, AgentEventType event, String message, String summary, String error) {
        s.transitionTo(status, summary, error);
        sessions.flush();
        append(s, event, message);
    }
    private void append(AgentSession s, AgentEventType type, String message) {
        events.save(new AgentEvent(s, type, message, events.countByAgentSession_Id(s.getId()) + 1));
    }
    public record StartRequested(UUID sessionId) { }
}
