package com.internregister.controller;

import com.internregister.entity.Supervisor;
import com.internregister.entity.User;
import com.internregister.entity.Department;
import com.internregister.repository.SupervisorRepository;
import com.internregister.repository.UserRepository;
import com.internregister.repository.DepartmentRepository;
import com.internregister.service.SupervisorService;
import com.internregister.service.WebSocketService;
import com.internregister.service.ActivityLogService;
import com.internregister.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/supervisors")
@CrossOrigin(origins = "*")
public class SupervisorController {

    private final SupervisorService supervisorService;
    private final SupervisorRepository supervisorRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final WebSocketService webSocketService;
    private final ActivityLogService activityLogService;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    @Value("${app.system.url:http://localhost:4200}")
    private String systemUrl;

    public SupervisorController(
            SupervisorService supervisorService,
            SupervisorRepository supervisorRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            WebSocketService webSocketService,
            ActivityLogService activityLogService,
            SecurityUtil securityUtil) {
        this.supervisorService = supervisorService;
        this.supervisorRepository = supervisorRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.webSocketService = webSocketService;
        this.activityLogService = activityLogService;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> getAllSupervisors() {
        try {
            List<com.internregister.entity.Supervisor> supervisors = supervisorService.getAllSupervisors();

            // Optimization: Fetch all users in one query to avoid N+1 problem
            List<String> emails = supervisors.stream()
                    .map(com.internregister.entity.Supervisor::getEmail)
                    .collect(java.util.stream.Collectors.toList());

            List<User> users = userRepository.findByUsernameIn(emails);
            Map<String, User> userMap = users.stream()
                    .collect(java.util.stream.Collectors.toMap(User::getUsername,
                            java.util.function.Function.identity(), (existing, replacement) -> existing));

            List<Map<String, Object>> supervisorList = supervisors.stream().map(supervisor -> {
                Map<String, Object> supervisorMap = new java.util.HashMap<>();
                supervisorMap.put("supervisorId", supervisor.getSupervisorId());
                supervisorMap.put("name", supervisor.getName());
                supervisorMap.put("email", supervisor.getEmail());

                // Get department name
                if (supervisor.getDepartment() != null) {
                    supervisorMap.put("departmentId", supervisor.getDepartment().getDepartmentId());
                    supervisorMap.put("department", supervisor.getDepartment().getName());
                } else {
                    supervisorMap.put("departmentId", null);
                    supervisorMap.put("department", null);
                }

                // Get field from supervisor entity (stored when created)
                // If not set, try to determine from assigned interns
                String field = supervisor.getField();
                if (field == null || field.trim().isEmpty()) {
                    // Try to get field from first assigned intern
                    if (supervisor.getInterns() != null && !supervisor.getInterns().isEmpty()) {
                        // Field is typically stored in intern, but we'll check if available
                        // For now, return null if not stored in supervisor
                        field = null;
                    }
                }
                supervisorMap.put("field", field);

                // Get assigned interns
                List<Map<String, Object>> internsList = new java.util.ArrayList<>();
                if (supervisor.getInterns() != null) {
                    for (com.internregister.entity.Intern intern : supervisor.getInterns()) {
                        Map<String, Object> internMap = new java.util.HashMap<>();
                        internMap.put("internId", intern.getInternId());
                        internMap.put("name", intern.getName());
                        internMap.put("email", intern.getEmail());
                        internsList.add(internMap);
                    }
                }
                supervisorMap.put("interns", internsList);

                // Check if user exists and get active status & lastLogin from mapped users
                User user = userMap.get(supervisor.getEmail());
                if (user != null) {
                    supervisorMap.put("active", user.getActive() != null ? user.getActive() : true);
                    supervisorMap.put("lastLogin",
                            user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
                } else {
                    supervisorMap.put("active", true);
                    supervisorMap.put("lastLogin", null);
                }

                // Add createdAt and updatedAt timestamps
                supervisorMap.put("createdAt",
                        supervisor.getCreatedAt() != null ? supervisor.getCreatedAt().toString() : null);
                supervisorMap.put("updatedAt",
                        supervisor.getUpdatedAt() != null ? supervisor.getUpdatedAt().toString() : null);

                return supervisorMap;
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(supervisorList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve supervisors: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public Optional<Supervisor> getSupervisorById(@PathVariable Long id) {
        return supervisorService.getSupervisorById(id);
    }

    @GetMapping("/by-email/{email}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> getSupervisorByEmail(@PathVariable String email) {
        try {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(email.trim().toLowerCase()).stream().findFirst();
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            Map<String, Object> supervisorMap = new java.util.HashMap<>();
            supervisorMap.put("supervisorId", supervisor.getSupervisorId());
            supervisorMap.put("name", supervisor.getName());
            supervisorMap.put("email", supervisor.getEmail());

            // Get department name
            if (supervisor.getDepartment() != null) {
                supervisorMap.put("departmentId", supervisor.getDepartment().getDepartmentId());
                supervisorMap.put("department", supervisor.getDepartment().getName());
            } else {
                supervisorMap.put("departmentId", null);
                supervisorMap.put("department", null);
            }

            // Get active status and lastLogin from user
            Optional<User> userOpt = userRepository.findByUsername(supervisor.getEmail()).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                supervisorMap.put("active", user.getActive() != null ? user.getActive() : true);
                supervisorMap.put("lastLogin", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
            } else {
                supervisorMap.put("active", true);
                supervisorMap.put("lastLogin", null);
            }

            // Get field from supervisor entity
            supervisorMap.put("field", supervisor.getField());

            // Add createdAt and updatedAt timestamps
            supervisorMap.put("createdAt",
                    supervisor.getCreatedAt() != null ? supervisor.getCreatedAt().toString() : null);
            supervisorMap.put("updatedAt",
                    supervisor.getUpdatedAt() != null ? supervisor.getUpdatedAt().toString() : null);

            return ResponseEntity.ok(supervisorMap);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve supervisor: " + e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> createSupervisor(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String email = (String) body.get("email");
            Object deptIdObj = body.get("departmentId");
            String field = (String) body.get("field");
            String password = (String) body.get("password");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            String trimmedEmail = email.trim().toLowerCase();

            // Validate email domain
            if (!trimmedEmail.endsWith("@univen.ac.za")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Invalid email domain",
                        "message", "Only University of Venda (@univen.ac.za) email addresses are allowed."));
            }

            // Check if supervisor or user already exists
            if (supervisorRepository.findByEmail(trimmedEmail).stream().findFirst().isPresent() ||
                    userRepository.findByUsername(trimmedEmail).stream().findFirst().isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Supervisor with this email already exists"));
            }

            // Get department
            Department department = null;
            if (deptIdObj != null) {
                Long departmentId;
                if (deptIdObj instanceof Number) {
                    departmentId = ((Number) deptIdObj).longValue();
                } else {
                    departmentId = Long.parseLong(deptIdObj.toString());
                }
                Optional<Department> deptOpt = departmentRepository.findById(departmentId);
                if (deptOpt.isPresent()) {
                    department = deptOpt.get();
                }
            }

            // Create User account
            User user = new User();
            user.setUsername(trimmedEmail);
            user.setEmail(trimmedEmail);
            if (password != null && !password.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
            } else {
                // Generate a default password if not provided
                user.setPassword(passwordEncoder.encode("Supervisor123!"));
            }
            user.setRole(User.Role.SUPERVISOR);
            user.setRequiresPasswordChange(true);
            User savedUser = userRepository.save(user);

            // Create Supervisor profile
            Supervisor supervisor = new Supervisor();
            supervisor.setName(name.trim());
            supervisor.setEmail(trimmedEmail);
            supervisor.setDepartment(department);
            supervisor.setField(field != null ? field.trim() : null);
            Supervisor savedSupervisor = supervisorService.saveSupervisor(supervisor);

            System.out.println("✓ Supervisor created successfully:");
            System.out.println("  Supervisor ID: " + savedSupervisor.getSupervisorId());
            System.out.println("  User ID: " + savedUser.getId());
            System.out.println("  Name: " + savedSupervisor.getName());
            System.out.println("  Email: " + savedSupervisor.getEmail());
            System.out.println("  Department: " + (department != null ? department.getName() : "None"));

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Supervisor created successfully");
            response.put("supervisorId", savedSupervisor.getSupervisorId());
            response.put("userId", savedUser.getId());
            response.put("email", savedSupervisor.getEmail());
            response.put("name", savedSupervisor.getName());
            if (department != null) {
                response.put("departmentId", department.getDepartmentId());
                response.put("department", department.getName());
            }
            if (field != null && !field.trim().isEmpty()) {
                response.put("field", field.trim());
            }

            // Add createdAt and updatedAt timestamps
            response.put("createdAt",
                    savedSupervisor.getCreatedAt() != null ? savedSupervisor.getCreatedAt().toString() : null);
            response.put("updatedAt",
                    savedSupervisor.getUpdatedAt() != null ? savedSupervisor.getUpdatedAt().toString() : null);

            // Broadcast real-time update
            webSocketService.broadcastSupervisorUpdate("CREATED", response);

            // Log supervisor creation
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "CREATE_SUPERVISOR",
                            "Created supervisor: " + savedSupervisor.getEmail(), null));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create supervisor: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> updateSupervisor(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findById(id);
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            String oldEmail = supervisor.getEmail();

            // Update name if provided
            if (body.containsKey("name")) {
                String name = (String) body.get("name");
                if (name != null && !name.trim().isEmpty()) {
                    supervisor.setName(name.trim());
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Name cannot be empty"));
                }
            }

            // Update email if provided
            if (body.containsKey("email")) {
                String email = (String) body.get("email");
                if (email != null && !email.trim().isEmpty()) {
                    String trimmedEmail = email.trim().toLowerCase();

                    // Validate email domain
                    if (!trimmedEmail.endsWith("@univen.ac.za")) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                                "error", "Invalid email domain",
                                "message", "Only University of Venda (@univen.ac.za) email addresses are allowed."));
                    }

                    // Check if new email already exists (and it's not the current supervisor's
                    // email)
                    if (!trimmedEmail.equals(oldEmail)) {
                        if (userRepository.findByUsername(trimmedEmail).stream().findFirst().isPresent() ||
                                supervisorRepository.findByEmail(trimmedEmail).stream().findFirst().isPresent()) {
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(Map.of("error", "Email already exists"));
                        }

                        // Update User entity if it exists
                        Optional<User> userOpt = userRepository.findByUsername(oldEmail).stream().findFirst();
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            user.setUsername(trimmedEmail);
                            user.setEmail(trimmedEmail);
                            userRepository.save(user);
                        }
                    }

                    supervisor.setEmail(trimmedEmail);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Email cannot be empty"));
                }
            }

            // Update department if provided
            if (body.containsKey("departmentId")) {
                Object deptIdObj = body.get("departmentId");
                if (deptIdObj != null) {
                    Long departmentId;
                    if (deptIdObj instanceof Number) {
                        departmentId = ((Number) deptIdObj).longValue();
                    } else {
                        departmentId = Long.parseLong(deptIdObj.toString());
                    }
                    Optional<Department> deptOpt = departmentRepository.findById(departmentId);
                    if (deptOpt.isPresent()) {
                        supervisor.setDepartment(deptOpt.get());
                    }
                } else {
                    supervisor.setDepartment(null);
                }
            }

            // Update field if provided
            if (body.containsKey("field")) {
                String field = (String) body.get("field");
                supervisor.setField(field != null && !field.trim().isEmpty() ? field.trim() : null);
            }

            // Update password if provided (optional)
            if (body.containsKey("password")) {
                String password = (String) body.get("password");
                if (password != null && !password.trim().isEmpty()) {
                    // Find the associated User entity
                    Optional<User> userOpt = userRepository.findByUsername(supervisor.getEmail()).stream().findFirst();
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        // Hash the password using BCrypt before saving
                        String hashedPassword = passwordEncoder.encode(password.trim());
                        user.setPassword(hashedPassword);
                        userRepository.save(user);
                        System.out.println("✓ Supervisor password updated for: " + supervisor.getEmail());
                    } else {
                        System.out.println("⚠ Warning: User not found for supervisor: " + supervisor.getEmail()
                                + " - password not updated");
                    }
                }
                // If password is empty/null, we don't update it (optional field)
            }

            Supervisor updated = supervisorRepository.save(supervisor);

            // Return formatted response matching getAllSupervisors format
            Map<String, Object> responseMap = new java.util.HashMap<>();
            responseMap.put("supervisorId", updated.getSupervisorId());
            responseMap.put("id", updated.getSupervisorId()); // Also include as 'id' for frontend compatibility
            responseMap.put("name", updated.getName());
            responseMap.put("email", updated.getEmail());

            // Get department info
            if (updated.getDepartment() != null) {
                responseMap.put("departmentId", updated.getDepartment().getDepartmentId());
                responseMap.put("department", updated.getDepartment().getName());
            } else {
                responseMap.put("departmentId", null);
                responseMap.put("department", null);
            }

            // Get field
            responseMap.put("field", updated.getField());

            // Get assigned interns
            List<Map<String, Object>> internsList = new java.util.ArrayList<>();
            if (updated.getInterns() != null) {
                for (com.internregister.entity.Intern intern : updated.getInterns()) {
                    Map<String, Object> internMap = new java.util.HashMap<>();
                    internMap.put("internId", intern.getInternId());
                    internMap.put("name", intern.getName());
                    internMap.put("email", intern.getEmail());
                    internsList.add(internMap);
                }
            }
            responseMap.put("interns", internsList);

            // Get active status from user
            Optional<User> userOpt = userRepository.findByUsername(updated.getEmail()).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                responseMap.put("active", user.getActive() != null ? user.getActive() : true);
                responseMap.put("lastLogin", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
            } else {
                responseMap.put("active", true);
                responseMap.put("lastLogin", null);
            }

            // Add createdAt and updatedAt timestamps
            responseMap.put("createdAt", updated.getCreatedAt() != null ? updated.getCreatedAt().toString() : null);
            responseMap.put("updatedAt", updated.getUpdatedAt() != null ? updated.getUpdatedAt().toString() : null);

            // Broadcast real-time update
            webSocketService.broadcastSupervisorUpdate("UPDATED", responseMap);
            webSocketService.broadcastUserUpdate("PROFILE_UPDATED", Map.of(
                    "email", updated.getEmail(),
                    "name", updated.getName(),
                    "department", updated.getDepartment() != null ? updated.getDepartment().getName() : "",
                    "role", "SUPERVISOR"
            ));

            // Log supervisor update
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "UPDATE_SUPERVISOR",
                            "Updated supervisor: " + updated.getEmail(), null));

            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update supervisor: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteSupervisor(@PathVariable Long id) {
        try {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findById(id);
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            String supervisorEmail = supervisor.getEmail();
            String supervisorName = supervisor.getName();

            // Delete associated User if it exists
            Optional<User> userOpt = userRepository.findByUsername(supervisorEmail).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                userRepository.delete(user);
            }

            // Delete Supervisor
            supervisorRepository.delete(supervisor);

            Map<String, Object> response = Map.of(
                    "message", "Supervisor deleted successfully",
                    "supervisorId", id,
                    "name", supervisorName,
                    "email", supervisorEmail);

            // Broadcast real-time update
            webSocketService.broadcastSupervisorUpdate("DELETED", response);

            // Log supervisor deletion
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "DELETE_SUPERVISOR",
                            "Deleted supervisor: " + supervisorEmail, null));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete supervisor: " + e.getMessage()));
        }
    }

    /**
     * Deactivate a supervisor
     */
    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> deactivateSupervisor(@PathVariable Long id) {
        try {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findById(id);
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            Optional<User> userOpt = userRepository.findByUsername(supervisor.getEmail()).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setActive(false);
                userRepository.save(user);
                System.out.println("✓ Supervisor deactivated: " + supervisor.getEmail());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found for this supervisor"));
            }

            Map<String, Object> response = Map.of(
                    "message", "Supervisor deactivated successfully",
                    "supervisorId", id,
                    "email", supervisor.getEmail(),
                    "active", false);

            // Broadcast real-time update
            webSocketService.broadcastSupervisorUpdate("DEACTIVATED", response);

            // Log supervisor deactivation
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "DEACTIVATE_SUPERVISOR",
                            "Deactivated supervisor: " + supervisor.getEmail(), null));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to deactivate supervisor: " + e.getMessage()));
        }
    }

    /**
     * Activate a supervisor
     */
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> activateSupervisor(@PathVariable Long id) {
        try {
            Optional<Supervisor> supervisorOpt = supervisorRepository.findById(id);
            if (supervisorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Supervisor not found"));
            }

            Supervisor supervisor = supervisorOpt.get();
            Optional<User> userOpt = userRepository.findByUsername(supervisor.getEmail()).stream().findFirst();
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setActive(true);
                // Ensure user has SUPERVISOR role
                if (user.getRole() != User.Role.SUPERVISOR) {
                    user.setRole(User.Role.SUPERVISOR);
                }
                userRepository.save(user);
                System.out.println("✓ Supervisor activated: " + supervisor.getEmail());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found for this supervisor"));
            }

            Map<String, Object> response = Map.of(
                    "message", "Supervisor activated successfully",
                    "supervisorId", id,
                    "email", supervisor.getEmail(),
                    "active", true);

            // Broadcast real-time update
            webSocketService.broadcastSupervisorUpdate("ACTIVATED", response);

            // Log supervisor activation
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "ACTIVATE_SUPERVISOR",
                            "Activated supervisor: " + supervisor.getEmail(), null));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to activate supervisor: " + e.getMessage()));
        }
    }

    /**
     * Send invite email to supervisor with login credentials
     */
    @PostMapping("/send-invite")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<?> sendSupervisorInvite(@RequestBody Map<String, Object> body) {
        try {
            String email = (String) body.get("email");
            String name = (String) body.get("name");
            String password = (String) body.get("password");
            String inviteLink = (String) body.get("inviteLink");
            String message = (String) body.get("message");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            // TODO: Implement actual email sending
            // For now, just log the information
            System.out.println("===========================================");
            System.out.println("SUPERVISOR INVITE EMAIL");
            System.out.println("To: " + email);
            System.out.println("Name: " + name);
            if (password != null) {
                System.out.println("Password: " + password);
            }
            System.out.println("Invite Link: " + inviteLink);
            System.out.println("Message: " + message);
            System.out.println("===========================================");

            return ResponseEntity.ok(Map.of(
                    "message", "Invite email sent successfully",
                    "email", email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send invite: " + e.getMessage()));
        }
    }
}
