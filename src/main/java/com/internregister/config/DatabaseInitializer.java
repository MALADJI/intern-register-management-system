package com.internregister.config;

import com.internregister.entity.User;
import com.internregister.repository.UserRepository;
import com.internregister.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DatabaseInitializer(UserRepository userRepository, @Lazy UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("\n=== Starting Database Initialization ===");

        ensureUserExists("superadmin@univen.ac.za", "admin123", User.Role.SUPER_ADMIN);
        ensureUserExists("admin@univen.ac.za", "admin123", User.Role.ADMIN);
        ensureUserExists("supervisor@univen.ac.za", "supervisor123", User.Role.SUPERVISOR);
        ensureUserExists("intern@univen.ac.za", "intern123", User.Role.INTERN);

        System.out.println("=== Database Initialization Complete ===\n");
    }

    private void ensureUserExists(String email, String password, User.Role role) {
        if (userRepository.findByUsername(email).isEmpty()) {
            User user = new User();
            user.setUsername(email);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(role);
            user.setActive(true);
            User saved = userService.saveUser(user);
            System.out.println("✓ Created " + role + " user: " + email + " (ID: " + saved.getId() + ")");
        } else {
            System.out.println("- User already exists: " + email + " (" + role + ")");
        }
    }
}
