package ru.savostov.sre_platform.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.savostov.sre_platform.model.agentSession.*;
import java.util.*;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AgentSession s where s.id = :id")
    Optional<AgentSession> lockById(@Param("id") UUID id);
    List<AgentSession> findByService_IdOrderByCreatedAtDescIdDesc(UUID serviceId);
    @Query("select s.id from AgentSession s where s.status in :statuses")
    List<UUID> findIdsByStatusIn(@Param("statuses") Collection<AgentSessionStatus> statuses);
}
