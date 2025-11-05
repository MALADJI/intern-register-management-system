package com.internregister.service;

import com.internregister.entity.PasswordResetToken;
import com.internregister.entity.User;
import com.internregister.repository.PasswordResetTokenRepository;
import com.internregister.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository, UserService userService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Transactional
    public String createResetToken(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken(generateToken());
        prt.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenRepository.save(prt);
        return prt.getToken();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token).orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token expired or used");
        }
        User user = prt.getUser();
        user.setPassword(newPassword);
        userService.saveUser(user);
        prt.setUsed(true);
        tokenRepository.save(prt);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}


