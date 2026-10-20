package ru.savostov.sre_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.savostov.sre_platform.model.agentEvent.AgentEvent;
import java.util.*;

public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {
    List<AgentEvent> findByAgentSession_IdOrderBySequenceNumberAsc(UUID sessionId);
    long countByAgentSession_Id(UUID sessionId);
}
