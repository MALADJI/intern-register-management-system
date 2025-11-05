package com.internregister.controller;

import com.internregister.entity.User;
import com.internregister.entity.Intern;
import com.internregister.entity.Supervisor;
import com.internregister.entity.Admin;
import com.internregister.entity.NotificationPreference;
import com.internregister.repository.UserRepository;
import com.internregister.repository.InternRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.repository.AdminRepository;
import com.internregister.service.UserService;
import com.internregister.service.AuthHelperService;
import com.internregister.service.NotificationPreferenceService;
import com.internregister.service.TermsAcceptanceService;
import com.internregister.security.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private InternRepository internRepository;
    
    @Autowired
    private SupervisorRepository supervisorRepository;
    
    @Autowired
    private AdminRepository adminRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordValidator passwordValidator;
    
    @Autowired(required = false)
    private AuthHelperService authHelperService;
    
    @Autowired(required = false)
    private NotificationPreferenceService notificationPreferenceService;
    
    @Autowired(required = false)
    private TermsAcceptanceService termsAcceptanceService;
    
    // Get current user profile with full details
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
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
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail() != null ? user.getEmail() : user.getUsername());
        profile.put("role", user.getRole().name());
        
        // Get role-specific profile data
        String role = user.getRole().name();
        if ("INTERN".equals(role)) {
            Optional<Intern> internOpt = internRepository.findByEmail(username);
            if (internOpt.isPresent()) {
                Intern intern = internOpt.get();
                profile.put("name", intern.getName());
                profile.put("surname", null); // Surname is stored as part of name field
                profile.put("department", intern.getDepartment() != null ? intern.getDepartment().getName() : null);
                profile.put("field", null); // Field property not available in Intern entity
                profile.put("supervisor", intern.getSupervisor() != null ? intern.getSupervisor().getName() : null);
                profile.put("supervisorEmail", intern.getSupervisor() != null ? intern.getSupervisor().getEmail() : null);
            }
        } else if ("SUPERVISOR".equals(role)) {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(username);
            if (supervisorOpt.isPresent()) {
                Supervisor supervisor = supervisorOpt.get();
                profile.put("name", supervisor.getName());
                profile.put("surname", null); // Surname is stored as part of name field
                profile.put("department", supervisor.getDepartment() != null ? supervisor.getDepartment().getName() : null);
            }
        } else if ("ADMIN".equals(role)) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(username);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                profile.put("name", admin.getName());
                profile.put("surname", null); // Surname is stored as part of name field
            }
        }
        
        return ResponseEntity.ok(profile);
    }
    
    // Update profile
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body) {
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
        
        try {
            // Update role-specific profile
            if ("INTERN".equals(role)) {
                Optional<Intern> internOpt = internRepository.findByEmail(username);
                if (internOpt.isPresent()) {
                    Intern intern = internOpt.get();
                    String name = body.get("name");
                    String surname = body.get("surname");
                    
                    if (name != null && surname != null) {
                        // If both name and surname are provided, combine them
                        intern.setName(name + " " + surname);
                    } else if (name != null) {
                        intern.setName(name);
                    } else if (surname != null) {
                        // If only surname is provided, combine with existing first name
                        String currentName = intern.getName() != null ? intern.getName().split(" ")[0] : "";
                        intern.setName(currentName + " " + surname);
                    }
                    internRepository.save(intern);
                }
            } else if ("SUPERVISOR".equals(role)) {
                Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(username);
                if (supervisorOpt.isPresent()) {
                    Supervisor supervisor = supervisorOpt.get();
                    String name = body.get("name");
                    String surname = body.get("surname");
                    
                    if (name != null && surname != null) {
                        // If both name and surname are provided, combine them
                        supervisor.setName(name + " " + surname);
                    } else if (name != null) {
                        supervisor.setName(name);
                    } else if (surname != null) {
                        // If only surname is provided, combine with existing first name
                        String currentName = supervisor.getName() != null ? supervisor.getName().split(" ")[0] : "";
                        supervisor.setName(currentName + " " + surname);
                    }
                    supervisorRepository.save(supervisor);
                }
            } else if ("ADMIN".equals(role)) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(username);
                if (adminOpt.isPresent()) {
                    Admin admin = adminOpt.get();
                    String name = body.get("name");
                    String surname = body.get("surname");
                    
                    if (name != null && surname != null) {
                        // If both name and surname are provided, combine them
                        admin.setName(name + " " + surname);
                    } else if (name != null) {
                        admin.setName(name);
                    } else if (surname != null) {
                        // If only surname is provided, combine with existing first name
                        String currentName = admin.getName() != null ? admin.getName().split(" ")[0] : "";
                        admin.setName(currentName + " " + surname);
                    }
                    adminRepository.save(admin);
                }
            }
            
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update profile: " + e.getMessage()));
        }
    }
    
    // Change password
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body) {
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
        
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password and new password are required"));
        }
        
        User user = userOpt.get();
        
        // Verify current password
        if (!userService.checkPassword(currentPassword, user.getPassword())) {
            return ResponseEntity.status(400).body(Map.of("error", "Current password is incorrect"));
        }
        
        // Validate new password
        PasswordValidator.PasswordValidationResult passwordValidation = passwordValidator.validate(newPassword);
        if (!passwordValidation.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("error", passwordValidation.getErrorMessage()));
        }
        
        // Update password
        user.setPassword(newPassword);
        userService.saveUser(user);
        
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
    
    // Notification preferences endpoints (if services are available)
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotificationPreferences() {
        if (authHelperService == null || notificationPreferenceService == null) {
            return ResponseEntity.status(501).body(Map.of("error", "Notification preferences not available"));
        }
        var userOpt = authHelperService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        User user = userOpt.get();
        NotificationPreference pref = notificationPreferenceService.getOrCreateFor(user);
        return ResponseEntity.ok(Map.of(
                "emailLeaveUpdates", pref.isEmailLeaveUpdates(),
                "emailAttendanceAlerts", pref.isEmailAttendanceAlerts(),
                "frequency", pref.getFrequency().name()
        ));
    }

    @PutMapping("/notifications")
    public ResponseEntity<?> updateNotificationPreferences(@RequestBody Map<String, Object> body) {
        if (authHelperService == null || notificationPreferenceService == null) {
            return ResponseEntity.status(501).body(Map.of("error", "Notification preferences not available"));
        }
        var userOpt = authHelperService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        User user = userOpt.get();

        NotificationPreference updated = new NotificationPreference();
        if (body.containsKey("emailLeaveUpdates")) {
            updated.setEmailLeaveUpdates(Boolean.TRUE.equals(body.get("emailLeaveUpdates")) || Boolean.TRUE.equals(Boolean.valueOf(String.valueOf(body.get("emailLeaveUpdates")))));
        }
        if (body.containsKey("emailAttendanceAlerts")) {
            updated.setEmailAttendanceAlerts(Boolean.TRUE.equals(body.get("emailAttendanceAlerts")) || Boolean.TRUE.equals(Boolean.valueOf(String.valueOf(body.get("emailAttendanceAlerts")))));
        }
        if (body.containsKey("frequency")) {
            try {
                updated.setFrequency(NotificationPreference.Frequency.valueOf(String.valueOf(body.get("frequency")).toUpperCase()));
            } catch (Exception ignored) {}
        }

        NotificationPreference saved = notificationPreferenceService.update(user, updated);
        return ResponseEntity.ok(Map.of(
                "emailLeaveUpdates", saved.isEmailLeaveUpdates(),
                "emailAttendanceAlerts", saved.isEmailAttendanceAlerts(),
                "frequency", saved.getFrequency().name()
        ));
    }

    @GetMapping("/terms")
    public ResponseEntity<?> getTermsStatus() {
        if (authHelperService == null || termsAcceptanceService == null) {
            return ResponseEntity.status(501).body(Map.of("error", "Terms acceptance not available"));
        }
        var userOpt = authHelperService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        var ta = termsAcceptanceService.getOrCreate(userOpt.get());
        return ResponseEntity.ok(Map.of(
                "accepted", ta.isAccepted(),
                "acceptedAt", ta.getAcceptedAt(),
                "version", ta.getVersion()
        ));
    }

    @PutMapping("/terms")
    public ResponseEntity<?> acceptTerms(@RequestBody Map<String, String> body, @RequestHeader(value = "X-Forwarded-For", required = false) String xff, @RequestHeader(value = "X-Real-IP", required = false) String xri) {
        if (authHelperService == null || termsAcceptanceService == null) {
            return ResponseEntity.status(501).body(Map.of("error", "Terms acceptance not available"));
        }
        var userOpt = authHelperService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        String version = body != null ? body.getOrDefault("version", "v1") : "v1";
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : (xri != null ? xri : null);
        var saved = termsAcceptanceService.accept(userOpt.get(), version, ip);
        return ResponseEntity.ok(Map.of(
                "accepted", saved.isAccepted(),
                "acceptedAt", saved.getAcceptedAt(),
                "version", saved.getVersion()
        ));
    }
}
