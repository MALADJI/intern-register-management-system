package com.internregister.controller;

import com.internregister.entity.User;
import com.internregister.entity.Intern;
import com.internregister.entity.Admin;
import com.internregister.entity.Department;
import com.internregister.entity.Supervisor;
import com.internregister.repository.UserRepository;
import com.internregister.repository.InternRepository;
import com.internregister.repository.AdminRepository;
import com.internregister.repository.DepartmentRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.security.JwtTokenProvider;
import com.internregister.service.UserService;
import com.internregister.service.EmailVerificationService;
import com.internregister.security.PasswordValidator;
import com.internregister.security.RateLimitingService;
import com.internregister.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final InternRepository internRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final SupervisorRepository supervisorRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordValidator passwordValidator;
    private final RateLimitingService rateLimitingService;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider, UserRepository userRepository,
                          InternRepository internRepository, AdminRepository adminRepository,
                          DepartmentRepository departmentRepository,
                          SupervisorRepository supervisorRepository, EmailVerificationService emailVerificationService,
                          PasswordValidator passwordValidator, RateLimitingService rateLimitingService,
                          PasswordResetService passwordResetService) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.internRepository = internRepository;
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
        this.supervisorRepository = supervisorRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordValidator = passwordValidator;
        this.rateLimitingService = rateLimitingService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        System.out.println("=== LOGIN ATTEMPT ===");
        String username = body.get("username");
        String password = body.get("password");
        String clientIp = getClientIpAddress(request);
        
        System.out.println("Username: " + username);
        System.out.println("Password: " + (password != null ? "***" : "null"));
        System.out.println("Client IP: " + clientIp);
        
        // Check rate limiting
        if (rateLimitingService.isLockedOut(clientIp)) {
            long remainingSeconds = rateLimitingService.getRemainingLockoutSeconds(clientIp);
            System.out.println("⚠ Login blocked: IP " + clientIp + " is locked out. Remaining: " + remainingSeconds + " seconds");
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many failed login attempts. Please try again in " + (remainingSeconds / 60) + " minutes.",
                "lockoutSeconds", remainingSeconds
            ));
        }
        
        // Validate input
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            System.out.println("✗ Login attempt failed: Missing username or password");
            rateLimitingService.recordFailedAttempt(clientIp);
            return ResponseEntity.status(400).body(Map.of(
                "error", "Username and password are required",
                "message", "Please provide both username/email and password"
            ));
        }
        
        // Find user by username or email (case-insensitive)
        User user = userService.findByUsernameOrEmail(username.trim()).orElse(null);
        if (user == null) {
            System.out.println("✗ Login attempt failed: User not found - " + username.trim());
            System.out.println("  Searched for username/email: " + username.trim());
            rateLimitingService.recordFailedAttempt(clientIp);
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", "Invalid username/email or password. Please check your credentials and try again."
            ));
        }
        
        System.out.println("✓ User found: " + user.getUsername() + " (Role: " + user.getRole() + ", Email: " + user.getEmail() + ")");
        
        // Verify password - CRITICAL: This must always be checked
        boolean passwordValid = userService.checkPassword(password, user.getPassword());
        System.out.println("Password valid: " + passwordValid);
        
        if (!passwordValid) {
            System.out.println("✗ Login attempt failed: Invalid password for user - " + user.getUsername());
            rateLimitingService.recordFailedAttempt(clientIp);
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", "Invalid username/email or password. Please check your credentials and try again."
            ));
        }
        
        // Successful login - clear rate limiting
        rateLimitingService.clearAttempts(clientIp);
        
        // Only generate token if password is valid
        System.out.println("✓ Login successful: " + username.trim() + " (Role: " + user.getRole() + ")");
        String token = jwtTokenProvider.createToken(user);
        System.out.println("=== LOGIN SUCCESS ===");
        return ResponseEntity.ok(Map.of(
            "token", token,
            "role", user.getRole().name(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : user.getUsername()
        ));
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
        String username = authentication.getName();
        Optional<User> userOpt = userService.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : user.getUsername(),
            "role", user.getRole().name()
        ));
    }
    
    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmailExists(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Email is required"));
        }
        
        // Check if email exists in User table (case-insensitive)
        boolean exists = userRepository.existsByUsername(email.trim()) || 
                         userRepository.existsByEmail(email.trim());
        
        if (exists) {
            return ResponseEntity.ok(Map.of("exists", true, "message", "Email already registered"));
        } else {
            return ResponseEntity.ok(Map.of("exists", false, "message", "Email is available"));
        }
    }
    
    @PostMapping("/send-verification-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        
        try {
            String code = emailVerificationService.generateAndStoreCode(email.trim());
            return ResponseEntity.ok(Map.of(
                "message", "Verification code sent to " + email,
                "code", code // Remove this in production - only for testing
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send verification code"));
        }
    }
    
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String purpose = body.get("purpose"); // "signup" or "password-reset"
        
        if (email == null || code == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and code are required"));
        }
        
        // For password reset, don't delete the code (allow reuse)
        // For signup, delete after verification (one-time use)
        boolean deleteAfterVerification = !"password-reset".equals(purpose);
        boolean isValid = emailVerificationService.verifyCode(email.trim(), code.trim(), deleteAfterVerification);
        
        if (isValid) {
            return ResponseEntity.ok(Map.of("valid", true, "message", "Code verified successfully"));
        } else {
            return ResponseEntity.status(400).body(Map.of("valid", false, "error", "Invalid or expired verification code"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String,String> request) {
        try {
            String email = request.get("username");
            String verificationCode = request.get("verificationCode");
            String password = request.get("password");
            String roleStr = request.get("role");
            
            // Validate email
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            
            // Validate email format
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format"));
            }
            
            // Validate verification code
            if (verificationCode == null || verificationCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Verification code is required"));
            }
            
            // Verify the code (delete after verification - one-time use for registration)
            if (!emailVerificationService.verifyCode(email.trim(), verificationCode.trim(), true)) {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired verification code"));
            }
            
            // Validate password
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            // Validate password strength
            PasswordValidator.PasswordValidationResult passwordValidation = passwordValidator.validate(password);
            if (!passwordValidation.isValid()) {
                return ResponseEntity.badRequest().body(Map.of("error", passwordValidation.getErrorMessage()));
            }
            
            // Validate role
            if (roleStr == null || roleStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
            }
            
            // Validate role format
            User.Role role;
            try {
                role = User.Role.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Must be ADMIN, SUPERVISOR, or INTERN"));
            }
            
            // Check if email/username already exists (case-insensitive)
            String trimmedEmail = email.trim();
            if (userRepository.existsByUsername(trimmedEmail) || 
                userRepository.existsByEmail(trimmedEmail)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "error", "Email already registered", 
                        "message", "This email is already registered. Please use a different email or try logging in."
                    ));
            }
            
            User user = new User();
            user.setUsername(trimmedEmail);
            user.setEmail(trimmedEmail);
            user.setPassword(password);
            user.setRole(role);
            
            User savedUser = userService.saveUser(user);
            System.out.println("✓ Password hashed and saved for user: " + savedUser.getUsername());
            System.out.println("✓ New user registered and saved to database:");
            System.out.println("  Username: " + savedUser.getUsername());
            System.out.println("  Role: " + savedUser.getRole());
            System.out.println("  ID: " + savedUser.getId());
            
            // Auto-create profile based on role
            if (savedUser.getRole() == User.Role.INTERN) {
                createInternProfile(savedUser, request);
            } else if (savedUser.getRole() == User.Role.SUPERVISOR) {
                createSupervisorProfile(savedUser, request);
            } else if (savedUser.getRole() == User.Role.ADMIN) {
                createAdminProfile(savedUser, request);
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "User registered successfully",
                "userId", savedUser.getId().toString(),
                "role", savedUser.getRole().name()
            ));
        } catch (Exception e) {
            System.err.println("Error registering user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to register user: " + e.getMessage()));
        }
    }
    
    private void createInternProfile(User user, Map<String, String> request) {
        try {
            // Check if intern already exists
            Optional<Intern> existingIntern = internRepository.findByEmail(user.getUsername());
            if (existingIntern.isPresent()) {
                System.out.println("Intern profile already exists for: " + user.getUsername());
                return;
            }
            
            String name = request.getOrDefault("name", user.getUsername().split("@")[0]);
            String surname = request.getOrDefault("surname", "");
            String fullName = surname.isEmpty() ? name : name + " " + surname;
            String departmentName = request.getOrDefault("department", "ICT");
            
            // Find or create department
            Department department = departmentRepository.findByName(departmentName)
                .orElseGet(() -> {
                    Department dept = new Department();
                    dept.setName(departmentName);
                    Department saved = departmentRepository.save(dept);
                    System.out.println("✓ Created department: " + departmentName);
                    return saved;
                });
            
            // Find or create default supervisor (first supervisor in department, or create one)
            Supervisor supervisor = supervisorRepository.findAll().stream()
                .filter(s -> s.getDepartment() != null && s.getDepartment().getDepartmentId().equals(department.getDepartmentId()))
                .findFirst()
                .orElseGet(() -> {
                    // Create default supervisor for department
                    Supervisor sup = new Supervisor();
                    sup.setName("Default Supervisor");
                    sup.setEmail("supervisor@" + departmentName.toLowerCase().replace(" ", "") + ".univen.ac.za");
                    sup.setDepartment(department);
                    Supervisor saved = supervisorRepository.save(sup);
                    System.out.println("✓ Created default supervisor for department: " + departmentName);
                    return saved;
                });
            
            // Create intern
            Intern intern = new Intern();
            intern.setName(fullName);
            intern.setEmail(user.getUsername());
            intern.setDepartment(department);
            intern.setSupervisor(supervisor);
            
            Intern savedIntern = internRepository.save(intern);
            System.out.println("✓ Intern profile created and saved to database:");
            System.out.println("  Name: " + savedIntern.getName());
            System.out.println("  Email: " + savedIntern.getEmail());
            System.out.println("  Department: " + department.getName());
            System.out.println("  Supervisor: " + supervisor.getName());
            System.out.println("  Intern ID: " + savedIntern.getInternId());
        } catch (Exception e) {
            System.err.println("Error creating intern profile: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createSupervisorProfile(User user, Map<String, String> request) {
        try {
            // Check if supervisor already exists
            Optional<Supervisor> existingSupervisor = supervisorRepository.findByEmail(user.getUsername());
            if (existingSupervisor.isPresent()) {
                System.out.println("Supervisor profile already exists for: " + user.getUsername());
                return;
            }
            
            String name = request.getOrDefault("name", user.getUsername().split("@")[0]);
            String surname = request.getOrDefault("surname", "");
            String fullName = surname.isEmpty() ? name : name + " " + surname;
            String departmentName = request.getOrDefault("department", "ICT");
            
            // Find or create department
            Department department = departmentRepository.findByName(departmentName)
                .orElseGet(() -> {
                    Department dept = new Department();
                    dept.setName(departmentName);
                    Department saved = departmentRepository.save(dept);
                    System.out.println("✓ Created department: " + departmentName);
                    return saved;
                });
            
            // Create supervisor
            Supervisor supervisor = new Supervisor();
            supervisor.setName(fullName);
            supervisor.setEmail(user.getUsername());
            supervisor.setDepartment(department);
            
            Supervisor savedSupervisor = supervisorRepository.save(supervisor);
            System.out.println("✓ Supervisor profile created and saved to database:");
            System.out.println("  Name: " + savedSupervisor.getName());
            System.out.println("  Email: " + savedSupervisor.getEmail());
            System.out.println("  Department: " + department.getName());
            System.out.println("  Supervisor ID: " + savedSupervisor.getSupervisorId());
        } catch (Exception e) {
            System.err.println("Error creating supervisor profile: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createAdminProfile(User user, Map<String, String> request) {
        try {
            // Check if admin already exists
            Optional<Admin> existingAdmin = adminRepository.findByEmail(user.getUsername());
            if (existingAdmin.isPresent()) {
                System.out.println("Admin profile already exists for: " + user.getUsername());
                return;
            }
            
            String name = request.getOrDefault("name", user.getUsername().split("@")[0]);
            String surname = request.getOrDefault("surname", "");
            String fullName = surname.isEmpty() ? name : name + " " + surname;
            
            // Create admin
            Admin admin = new Admin();
            admin.setName(fullName);
            admin.setEmail(user.getUsername());
            
            Admin savedAdmin = adminRepository.save(admin);
            System.out.println("✓ Admin profile created and saved to database:");
            System.out.println("  Name: " + savedAdmin.getName());
            System.out.println("  Email: " + savedAdmin.getEmail());
            System.out.println("  Admin ID: " + savedAdmin.getAdminId());
        } catch (Exception e) {
            System.err.println("Error creating admin profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        
        Optional<User> userOpt = userService.findByUsername(email.trim());
        
        try {
            // Always generate and return code for testing (even if user doesn't exist)
            // In production, you might want to only generate if user exists
            String code = emailVerificationService.generateAndStoreCode(email.trim(), true); // true = password reset
            
            if (userOpt.isEmpty()) {
                // Don't reveal if user exists for security, but still return code for testing
                return ResponseEntity.ok(Map.of(
                    "message", "If an account exists with this email, a verification code has been sent.",
                    "code", code // For testing - shown in popup
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "If an account exists with this email, a verification code has been sent.",
                "code", code // For testing - shown in popup
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send verification code: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        System.out.println("=== RESET PASSWORD REQUEST ===");
        System.out.println("Request body: " + body);
        
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        
        // Log received values
        System.out.println("Email: " + email);
        System.out.println("Code: " + code);
        System.out.println("New Password: " + (newPassword != null ? "***" : "null"));
        
        if (email == null || email.trim().isEmpty()) {
            System.out.println("✗ Missing email");
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Email is required",
                "message", "Please provide your email address"
            ));
        }
        
        if (code == null || code.trim().isEmpty()) {
            System.out.println("✗ Missing verification code");
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Verification code is required",
                "message", "Please enter the verification code sent to your email"
            ));
        }
        
        if (newPassword == null || newPassword.trim().isEmpty()) {
            System.out.println("✗ Missing new password");
            return ResponseEntity.badRequest().body(Map.of(
                "error", "New password is required",
                "message", "Please enter a new password"
            ));
        }
        
        // Verify code (don't delete - allow reuse for password reset)
        System.out.println("Verifying code for email: " + email.trim() + ", code: " + code.trim());
        boolean codeValid = emailVerificationService.verifyCode(email.trim(), code.trim(), false);
        if (!codeValid) {
            System.out.println("✗ Code verification failed");
            // Check if code exists but expired
            Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "Invalid or expired verification code");
            errorResponse.put("message", "The verification code is invalid or has expired. Please request a new code.");
            errorResponse.put("errorCode", "INVALID_CODE");
            return ResponseEntity.status(400).body(errorResponse);
        }
        System.out.println("✓ Code verified successfully");
        
        // Find user by email or username
        Optional<User> userOpt = userService.findByUsernameOrEmail(email.trim());
        if (userOpt.isEmpty()) {
            System.out.println("✗ User not found: " + email.trim());
            Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "User not found");
            errorResponse.put("message", "No account found with email: " + email.trim() + ". Please check your email address.");
            errorResponse.put("errorCode", "USER_NOT_FOUND");
            return ResponseEntity.status(404).body(errorResponse);
        }
        System.out.println("✓ User found: " + email.trim());
        
        // Validate password strength
        PasswordValidator.PasswordValidationResult passwordValidation = passwordValidator.validate(newPassword);
        if (!passwordValidation.isValid()) {
            System.out.println("✗ Password validation failed: " + passwordValidation.getErrorMessage());
            Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", passwordValidation.getErrorMessage());
            errorResponse.put("message", passwordValidation.getErrorMessage() + " Password must be at least 8 characters with uppercase, lowercase, number, and special character (@$!%*?&).");
            errorResponse.put("errorCode", "INVALID_PASSWORD");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        System.out.println("✓ Password validation passed");
        
        // Update password
        User user = userOpt.get();
        user.setPassword(newPassword);
        userService.saveUser(user);
        System.out.println("✓ Password reset successfully for user: " + email.trim());
        System.out.println("=== RESET PASSWORD SUCCESS ===");
        
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }
}
