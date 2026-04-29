package com.internregister.controller;

import com.internregister.entity.Admin;
import com.internregister.entity.User;
import com.internregister.entity.Supervisor;
import com.internregister.service.AdminService;
import com.internregister.repository.UserRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.service.WebSocketService;
import com.internregister.service.ActivityLogService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admins")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final SupervisorRepository supervisorRepository;
    private final SecurityUtil securityUtil;
    private final WebSocketService webSocketService;
    private final ActivityLogService activityLogService;

    public AdminController(AdminService adminService, UserRepository userRepository,
            SupervisorRepository supervisorRepository, SecurityUtil securityUtil,
            WebSocketService webSocketService, ActivityLogService activityLogService) {
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.supervisorRepository = supervisorRepository;
        this.securityUtil = securityUtil;
        this.webSocketService = webSocketService;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Admin> getAllAdmins() {
        return adminService.getAllAdmins();
    }

    /**
     * Get all users (for admin dashboard)
     * Accessible by ADMIN and SUPER_ADMIN
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        try {
            // Check if user is ADMIN or SUPER_ADMIN
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Not authenticated"));
            }

            User currentUser = currentUserOpt.get();
            User.Role role = currentUser.getRole();

            if (role != User.Role.ADMIN && role != User.Role.SUPER_ADMIN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. ADMIN or SUPER_ADMIN role required."));
            }

            List<User> users = userRepository.findAll();
            List<Map<String, Object>> userList = users.stream().map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("email", user.getEmail() != null ? user.getEmail() : user.getUsername());
                userMap.put("role", user.getRole().name());
                userMap.put("active", user.getActive() != null ? user.getActive() : true);
                userMap.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
                userMap.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
                return userMap;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(userList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve users: " + e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Admin createAdmin(@RequestBody Admin admin) {
        Admin saved = adminService.saveAdmin(admin);

        // Broadcast real-time update
        webSocketService.broadcastAdminUpdate("CREATED", saved);

        // Log admin creation
        securityUtil.getCurrentUser()
                .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "CREATE_ADMIN",
                        "Created admin: " + saved.getEmail(), null));

        return saved;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteAdmin(@PathVariable Long id) {
        // Get admin details before deletion for logging
        Admin admin = adminService.getAdminById(id);
        String adminEmail = (admin != null) ? admin.getEmail() : "ID: " + id;

        adminService.deleteAdmin(id);

        // Broadcast real-time update
        webSocketService.broadcastAdminUpdate("DELETED", Map.of("adminId", id));

        // Log admin deletion
        securityUtil.getCurrentUser()
                .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "DELETE_ADMIN",
                        "Deleted admin: " + adminEmail, null));
    }

    /**
     * Update supervisor signature (Admin only)
     */
    @PutMapping("/supervisors/{supervisorId}/signature")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> updateSupervisorSignature(@PathVariable Long supervisorId,
            @RequestBody Map<String, String> body) {
        try {
            // Check if user is ADMIN or SUPER_ADMIN
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Not authenticated"));
            }

            User currentUser = currentUserOpt.get();
            User.Role role = currentUser.getRole();

            if (role != User.Role.ADMIN && role != User.Role.SUPER_ADMIN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied. ADMIN or SUPER_ADMIN role required."));
            }

            String signature = body.get("signature");
            if (signature == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Signature is required"));
            }

            if (supervisorId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Supervisor ID is required"));
            }

            Optional<Supervisor> supervisorOpt = supervisorRepository.findById(supervisorId);
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            Optional<User> userOpt = userRepository.findByUsername(supervisor.getEmail()).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setSignature(signature);
                userRepository.save(user);

                // Broadcast real-time update
                Map<String, Object> broadcastData = new HashMap<>();
                broadcastData.put("supervisorId", supervisorId);
                broadcastData.put("hasSignature", true);
                broadcastData.put("email", supervisor.getEmail());
                webSocketService.broadcastSupervisorUpdate("SIGNATURE_UPDATED", broadcastData);

                // Log signature update
                activityLogService.log(currentUser.getUsername(), "UPDATE_SUPERVISOR_SIGNATURE",
                        "Updated signature for supervisor: " + supervisor.getEmail(), null);

                return ResponseEntity.ok(Map.of("message", "Supervisor signature updated successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User account not found for this supervisor"));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update supervisor signature: " + e.getMessage()));
        }
    }
}
