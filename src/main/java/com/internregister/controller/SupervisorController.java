package com.internregister.controller;

import com.internregister.entity.Supervisor;
import com.internregister.entity.User;
import com.internregister.dto.SupervisorRequest;
import com.internregister.service.SupervisorService;
import com.internregister.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/supervisors")
@CrossOrigin(origins = "*")
public class SupervisorController {

    private final SupervisorService supervisorService;
    private final UserRepository userRepository;

    public SupervisorController(SupervisorService supervisorService,
                               UserRepository userRepository) {
        this.supervisorService = supervisorService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Supervisor> getAllSupervisors() {
        return supervisorService.getAllSupervisors();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSupervisorById(@PathVariable Long id) {
        return supervisorService.getSupervisorById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createSupervisor(@Valid @RequestBody SupervisorRequest request) {
        try {
            Supervisor supervisor = supervisorService.createSupervisor(request);
            return ResponseEntity.ok(supervisor);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSupervisor(@PathVariable Long id, 
                                             @Valid @RequestBody SupervisorRequest request) {
        // Check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        String role = user.getRole().name();

        // Only admins can update supervisors
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can update supervisors"));
        }

        try {
            Supervisor updated = supervisorService.updateSupervisor(id, request);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update supervisor: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSupervisor(@PathVariable Long id) {
        try {
            supervisorService.deleteSupervisor(id);
            return ResponseEntity.ok().body(java.util.Map.of("message", "Supervisor deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
