package ru.savostov.sre_platform.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServerDiscoveryRepository extends JpaRepository<ServerDiscovery, UUID> {
    List<ServerDiscovery> findByStatusIn(List<DiscoveryModels.Status> statuses);
}
