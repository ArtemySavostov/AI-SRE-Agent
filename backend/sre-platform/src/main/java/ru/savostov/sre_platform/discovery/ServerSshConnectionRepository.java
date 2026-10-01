package ru.savostov.sre_platform.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ServerSshConnectionRepository extends JpaRepository<ServerSshConnection, UUID> { }
