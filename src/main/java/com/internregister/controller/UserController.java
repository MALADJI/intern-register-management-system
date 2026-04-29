package com.internregister.controller;

import com.internregister.entity.User;
import com.internregister.repository.UserRepository;
import com.internregister.service.UserService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    public UserController(UserRepository userRepository, SecurityUtil securityUtil) {
        this.userRepository = userRepository;
        this.securityUtil = securityUtil;
    }

    /**
     * Get current user's signature
     */
    @GetMapping("/me/signature")
    public ResponseEntity<?> getMySignature() {
        Optional<User> userOpt = securityUtil.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        User user = userOpt.get();
        boolean hasSignature = user.getSignature() != null && !user.getSignature().trim().isEmpty();
        return ResponseEntity.ok(Map.of(
            "hasSignature", hasSignature,
            "signature", user.getSignature() != null ? user.getSignature() : ""
        ));
    }

    /**
     * Update current user's signature
     */
    @PutMapping("/me/signature")
    public ResponseEntity<?> updateMySignature(@RequestBody Map<String, String> body) {
        Optional<User> userOpt = securityUtil.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String signature = body.get("signature");
        if (signature == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Signature is required"));
        }

        User user = userOpt.get();
        user.setSignature(signature);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "message", "Signature updated successfully",
            "hasSignature", true
        ));
    }
}

