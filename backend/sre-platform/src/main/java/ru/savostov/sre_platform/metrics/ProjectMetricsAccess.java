package ru.savostov.sre_platform.metrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.savostov.sre_platform.repository.UserRepository;
import ru.savostov.sre_platform.model.user.User;
import java.util.UUID;

@Component
public class ProjectMetricsAccess {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    public ProjectMetricsAccess(UserRepository users, JdbcTemplate jdbc) { this.users = users; this.jdbc = jdbc; }
    public void require(UUID projectId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Требуется вход");
        User user = users.findByEmail(authentication.getName()).filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Нет доступа"));
        if (user.getRole() == User.UserRole.ADMIN) return;
        Integer count = jdbc.queryForObject("select count(*) from project_metrics_access where project_id = ? and user_id = ?",
                Integer.class, projectId, user.getId());
        if (count == null || count == 0) throw new AccessDeniedException("Нет доступа к интеграциям проекта");
    }
}
