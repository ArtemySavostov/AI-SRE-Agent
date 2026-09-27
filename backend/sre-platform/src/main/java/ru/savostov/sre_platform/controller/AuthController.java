package ru.savostov.sre_platform.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.savostov.sre_platform.model.user.User;
import ru.savostov.sre_platform.service.UserService;

@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam(defaultValue = "") String username,
                               @RequestParam(defaultValue = "") String email,
                               @RequestParam(defaultValue = "") String password,
                               Model model) {
        try {
            userService.createUser(username, email, password);
            return "redirect:/login";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            // A concurrent registration can violate the unique email constraint.
            model.addAttribute("errorMessage", "Не удалось зарегистрироваться. Проверьте email и повторите попытку");
        }

        User user = new User();
        user.setName(username);
        user.setEmail(email);
        model.addAttribute("user", user);
        return "register";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }
}
