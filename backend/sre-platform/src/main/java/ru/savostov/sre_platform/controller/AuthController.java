package ru.savostov.sre_platform.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import ru.savostov.sre_platform.model.user.User;
import ru.savostov.sre_platform.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request) {
        try {
            userService.createUser(request.name(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint also protects against simultaneous registrations.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Не удалось зарегистрироваться. Проверьте email и повторите попытку"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName())
                .filter(User::isActive)
                .map(user -> ResponseEntity.ok(new UserResponse(user.getId(), user.getName(),
                        user.getEmail(), user.getRole())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public record RegistrationRequest(String name, String email, String password) {
        @Override
        public String toString() {
            return "RegistrationRequest[redacted]";
        }
    }
    public record UserResponse(UUID id, String name, String email, User.UserRole role) { }
    public record CsrfResponse(String headerName, String token) { }
    public record ErrorResponse(String message) { }
}
