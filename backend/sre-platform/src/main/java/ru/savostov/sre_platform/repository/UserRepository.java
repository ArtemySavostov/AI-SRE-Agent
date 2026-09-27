package ru.savostov.sre_platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.savostov.sre_platform.model.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
