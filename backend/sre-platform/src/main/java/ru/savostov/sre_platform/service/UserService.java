package ru.savostov.sre_platform.service;

import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.savostov.sre_platform.model.user.User;
import ru.savostov.sre_platform.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    public void createUser(String username, String email, String password) {
        if (username == null || username.isBlank() || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Заполните все поля");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Пароль должен занимать не более 72 байт в UTF-8");
        }

        User user = new User();
        user.setName(username.strip());
        user.setEmail(email.strip());
        if (!validator.validate(user).isEmpty()) {
            throw new IllegalArgumentException("Укажите корректный email и имя длиной не более 255 символов");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email.strip());
    }
}
