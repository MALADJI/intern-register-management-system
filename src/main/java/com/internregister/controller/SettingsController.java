package com.internregister.controller;

import com.internregister.entity.User;
import com.internregister.service.UserService;
import com.internregister.service.ActivityLogService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.internregister.service.WebSocketService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    private final UserService userService;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final com.internregister.repository.InternRepository internRepository;
    private final com.internregister.repository.AdminRepository adminRepository;
    private final com.internregister.repository.SupervisorRepository supervisorRepository;
    private final com.internregister.repository.SuperAdminRepository superAdminRepository;
    private final ActivityLogService activityLogService;
    private final WebSocketService webSocketService;

    public SettingsController(UserService userService, SecurityUtil securityUtil,
            com.internregister.repository.InternRepository internRepository,
            com.internregister.repository.AdminRepository adminRepository,
            com.internregister.repository.SupervisorRepository supervisorRepository,
            com.internregister.repository.SuperAdminRepository superAdminRepository,
            ActivityLogService activityLogService,
            WebSocketService webSocketService) {
        this.userService = userService;
        this.securityUtil = securityUtil;
        this.internRepository = internRepository;
        this.adminRepository = adminRepository;
        this.supervisorRepository = supervisorRepository;
        this.superAdminRepository = superAdminRepository;
        this.activityLogService = activityLogService;
        this.webSocketService = webSocketService;
    }

    /**
     * Get current user's profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Optional<User> userOpt = securityUtil.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        User user = userOpt.get();
        Map<String, Object> profile = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : user.getUsername(),
                "role", user.getRole().name());

        String email = user.getEmail();
        if (email == null) {
            return ResponseEntity.ok(profile);
        }

        if (user.getRole() == User.Role.INTERN) {
            java.util.Optional<com.internregister.entity.Intern> internOpt = internRepository
                    .findByEmail(email).stream().findFirst();
            if (internOpt.isPresent()) {
                com.internregister.entity.Intern intern = internOpt.get();
                java.util.Map<String, Object> internProfile = new java.util.HashMap<>(profile);

                String fullName = intern.getName();
                if (fullName != null) {
                    String[] parts = fullName.trim().split(" ", 2);
                    internProfile.put("name", parts[0]);
                    if (parts.length > 1) {
                        internProfile.put("surname", parts[1]);
                    }
                }

                if (intern.getDepartment() != null) {
                    internProfile.put("department", intern.getDepartment().getName());
                }
                internProfile.put("employer", intern.getEmployer());
                internProfile.put("idNumber", intern.getIdNumber());
                internProfile.put("startDate", intern.getStartDate());
                internProfile.put("endDate", intern.getEndDate());
                if (intern.getField() != null)
                    internProfile.put("field", intern.getField());
                return ResponseEntity.ok(internProfile);
            }
        } else if (user.getRole() == User.Role.ADMIN) {
            java.util.Optional<com.internregister.entity.Admin> adminOpt = adminRepository
                    .findByEmail(email).stream().findFirst();
            if (adminOpt.isPresent()) {
                com.internregister.entity.Admin admin = adminOpt.get();
                java.util.Map<String, Object> adminProfile = new java.util.HashMap<>(profile);

                String fullName = admin.getName();
                if (fullName != null) {
                    String[] parts = fullName.trim().split(" ", 2);
                    adminProfile.put("name", parts[0]);
                    if (parts.length > 1) {
                        adminProfile.put("surname", parts[1]);
                    }
                }

                if (admin.getDepartment() != null) {
                    adminProfile.put("department", admin.getDepartment().getName());
                }
                return ResponseEntity.ok(adminProfile);
            }
        } else if (user.getRole() == User.Role.SUPERVISOR) {
            java.util.Optional<com.internregister.entity.Supervisor> supervisorOpt = supervisorRepository
                    .findByEmail(email).stream().findFirst();
            if (supervisorOpt.isPresent()) {
                com.internregister.entity.Supervisor supervisor = supervisorOpt.get();
                java.util.Map<String, Object> supervisorProfile = new java.util.HashMap<>(profile);

                String fullName = supervisor.getName();
                if (fullName != null) {
                    String[] parts = fullName.trim().split(" ", 2);
                    supervisorProfile.put("name", parts[0]);
                    if (parts.length > 1) {
                        supervisorProfile.put("surname", parts[1]);
                    }
                }

                if (supervisor.getDepartment() != null) {
                    supervisorProfile.put("department", supervisor.getDepartment().getName());
                }
                if (supervisor.getField() != null) {
                    supervisorProfile.put("field", supervisor.getField());
                }
                return ResponseEntity.ok(supervisorProfile);
            }
        } else if (user.getRole() == User.Role.SUPER_ADMIN) {
            java.util.Optional<com.internregister.entity.SuperAdmin> superAdminOpt = superAdminRepository
                    .findByEmail(email).stream().findFirst();
            if (superAdminOpt.isPresent()) {
                com.internregister.entity.SuperAdmin superAdmin = superAdminOpt.get();
                java.util.Map<String, Object> superAdminProfile = new java.util.HashMap<>(profile);

                String fullName = superAdmin.getName();
                if (fullName != null) {
                    String[] parts = fullName.trim().split(" ", 2);
                    superAdminProfile.put("name", parts[0]);
                    if (parts.length > 1) {
                        superAdminProfile.put("surname", parts[1]);
                    }
                }
                return ResponseEntity.ok(superAdminProfile);
            }
        }

        return ResponseEntity.ok(profile);
    }

    /**
     * Update current user's profile
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body) {
        Optional<User> userOpt = securityUtil.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        User user = userOpt.get();

        // Update email if provided
        String email = body.get("email");
        if (email != null && !email.trim().isEmpty()) {
            String trimmedEmail = email.trim().toLowerCase();
            // Validate email domain
            if (!trimmedEmail.endsWith("@univen.ac.za")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Invalid email domain",
                        "message", "Only University of Venda (@univen.ac.za) email addresses are allowed."));
            }
            user.setEmail(trimmedEmail);
        }

        // Update username if provided (should match email)
        String username = body.get("username");
        if (username != null && !username.trim().isEmpty()) {
            user.setUsername(username.trim().toLowerCase());
        }

        userService.saveUser(user);

        String firstName = body.getOrDefault("name", "");
        String surname = body.getOrDefault("surname", "");
        String fullName = (firstName + " " + surname).trim();

        if (user.getRole() == User.Role.INTERN) {
            String emailToFind = user.getEmail();
            if (emailToFind == null) return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            java.util.Optional<com.internregister.entity.Intern> internOpt = internRepository
                    .findByEmail(emailToFind).stream().findFirst();
            if (internOpt.isPresent()) {
                com.internregister.entity.Intern intern = internOpt.get();

                if (!fullName.isEmpty())
                    intern.setName(fullName);
                if (body.containsKey("email"))
                    intern.setEmail(body.get("email"));
                if (body.containsKey("employer"))
                    intern.setEmployer(body.get("employer"));
                if (body.containsKey("idNumber"))
                    intern.setIdNumber(body.get("idNumber"));

                if (body.containsKey("startDate") && body.get("startDate") != null
                        && !body.get("startDate").isEmpty()) {
                    try {
                        intern.setStartDate(java.time.LocalDate.parse(body.get("startDate")));
                    } catch (Exception e) {
                        System.err.println("Error parsing startDate: " + e.getMessage());
                    }
                }
                if (body.containsKey("endDate") && body.get("endDate") != null && !body.get("endDate").isEmpty()) {
                    try {
                        intern.setEndDate(java.time.LocalDate.parse(body.get("endDate")));
                    } catch (Exception e) {
                        System.err.println("Error parsing endDate: " + e.getMessage());
                    }
                }

                internRepository.save(intern);
            }
        } else if (user.getRole() == User.Role.ADMIN) {
            String emailToFind = user.getEmail();
            if (emailToFind != null) {
                java.util.Optional<com.internregister.entity.Admin> adminOpt = adminRepository
                        .findByEmail(emailToFind).stream().findFirst();
                if (adminOpt.isPresent()) {
                    com.internregister.entity.Admin admin = adminOpt.get();
                    if (!fullName.isEmpty())
                        admin.setName(fullName);
                    if (body.containsKey("email"))
                        admin.setEmail(body.get("email"));
                    adminRepository.save(admin);
                }
            }
        } else if (user.getRole() == User.Role.SUPERVISOR) {
            String emailToFind = user.getEmail();
            if (emailToFind != null) {
                java.util.Optional<com.internregister.entity.Supervisor> supervisorOpt = supervisorRepository
                        .findByEmail(emailToFind).stream().findFirst();
                if (supervisorOpt.isPresent()) {
                    com.internregister.entity.Supervisor supervisor = supervisorOpt.get();
                    if (!fullName.isEmpty())
                        supervisor.setName(fullName);
                    if (body.containsKey("email"))
                        supervisor.setEmail(body.get("email"));
                    supervisorRepository.save(supervisor);
                }
            }
        } else if (user.getRole() == User.Role.SUPER_ADMIN) {
            String emailToFind = user.getEmail();
            if (emailToFind != null) {
                java.util.Optional<com.internregister.entity.SuperAdmin> superAdminOpt = superAdminRepository
                        .findByEmail(emailToFind).stream().findFirst();
                if (superAdminOpt.isPresent()) {
                    com.internregister.entity.SuperAdmin superAdmin = superAdminOpt.get();
                    if (!fullName.isEmpty())
                        superAdmin.setName(fullName);
                    if (body.containsKey("email"))
                        superAdmin.setEmail(body.get("email"));
                    superAdminRepository.save(superAdmin);
                }
            }
        }

        // Log profile update
        activityLogService.log(user.getUsername(), "UPDATE_PROFILE",
                "Updated " + user.getRole().name().toLowerCase() + " profile", null);

        Map<String, Object> responseData = Map.of(
                "message", "Profile updated successfully",
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : user.getUsername(),
                "role", user.getRole().name(),
                "name", fullName);
        
        webSocketService.broadcastUserUpdate("PROFILE_UPDATED", responseData);

        return ResponseEntity.ok(responseData);
    }

    /**
     * Change current user's password
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
        Optional<User> userOpt = securityUtil.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Current password and new password are required"));
        }

        User user = userOpt.get();

        // Verify current password
        if (!userService.checkPassword(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Current password is incorrect"));
        }

        // Validate new password strength (if PasswordValidator exists)
        // PasswordValidator passwordValidator = ...;
        // PasswordValidator.PasswordValidationResult validation =
        // passwordValidator.validate(newPassword);
        // if (!validation.isValid()) {
        // return ResponseEntity.badRequest().body(Map.of("error",
        // validation.getErrorMessage()));
        // }

        // Update password
        String hashed = passwordEncoder.encode(newPassword);
        if (hashed != null) {
            user.setPassword(hashed);
            user.setRequiresPasswordChange(false);
            userService.saveUser(user);
        }

        // Log password change
        activityLogService.log(user.getUsername(), "CHANGE_PASSWORD", "User changed their password", null);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
