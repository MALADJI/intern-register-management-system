package com.internregister.controller;

import com.internregister.entity.Intern;
import com.internregister.entity.Location;
import com.internregister.entity.User;
import com.internregister.entity.Admin;
import com.internregister.service.InternService;
import com.internregister.dto.InternRequest;
import com.internregister.dto.InternResponse;
import com.internregister.service.WebSocketService;
import com.internregister.util.SecurityUtil;
import com.internregister.repository.AdminRepository;
import com.internregister.repository.LocationRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.repository.UserRepository;
import com.internregister.service.ActivityLogService;
import com.internregister.service.EmailService;
import com.internregister.entity.Supervisor;
import com.internregister.dto.BulkInternRequest;
import com.internregister.dto.BulkInternInviteRequest;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.internregister.dto.InternResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Base64;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/interns")
@CrossOrigin(origins = "*")
public class InternController {

    private final InternService internService;
    private final SecurityUtil securityUtil;
    private final AdminRepository adminRepository;
    private final LocationRepository locationRepository;
    private final WebSocketService webSocketService;
    private final SupervisorRepository supervisorRepository;
    private final ActivityLogService activityLogService;
    private final EmailService emailService;

    public InternController(InternService internService,
            SecurityUtil securityUtil,
            AdminRepository adminRepository,
            LocationRepository locationRepository,
            WebSocketService webSocketService,
            SupervisorRepository supervisorRepository,
            ActivityLogService activityLogService,
            EmailService emailService) {
        this.internService = internService;
        this.securityUtil = securityUtil;
        this.adminRepository = adminRepository;
        this.locationRepository = locationRepository;
        this.webSocketService = webSocketService;
        this.supervisorRepository = supervisorRepository;
        this.activityLogService = activityLogService;
        this.emailService = emailService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> getAllInterns() {
        try {
            // Get current authenticated user
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                // If not authenticated, return all interns (for backward compatibility)
                return ResponseEntity.ok(internService.getAllInterns());
            }

            User currentUser = currentUserOpt.get();
            List<InternResponse> allInterns = internService.getAllInterns();

            // If user is ADMIN, filter by their department
            if (currentUser.getRole() == User.Role.ADMIN) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(currentUser.getEmail()).stream().findFirst();
                if (adminOpt.isPresent() && adminOpt.get().getDepartment() != null) {
                    Long adminDepartmentId = adminOpt.get().getDepartment().getDepartmentId();
                    // Filter interns by admin's department
                    List<InternResponse> filteredInterns = allInterns.stream()
                            .filter(intern -> intern.getDepartmentId() != null &&
                                    intern.getDepartmentId().equals(adminDepartmentId))
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(filteredInterns);
                }
            }

            // If user is SUPERVISOR, filter by their department/assignment
            if (currentUser.getRole() == User.Role.SUPERVISOR) {
                Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(currentUser.getEmail()).stream()
                        .findFirst();
                if (supervisorOpt.isPresent()) {
                    Supervisor supervisor = supervisorOpt.get();
                    Long deptId = supervisor.getDepartment() != null ? supervisor.getDepartment().getDepartmentId()
                            : null;

                    // Filter interns:
                    // 1. Must be in same department
                    // 2. OR Must be explicitly assigned to this supervisor
                    List<InternResponse> filteredInterns = allInterns.stream()
                            .filter(intern -> {
                                boolean sameDept = deptId != null && intern.getDepartmentId() != null
                                        && intern.getDepartmentId().equals(deptId);
                                boolean assigned = intern.getSupervisorId() != null
                                        && intern.getSupervisorId().equals(supervisor.getSupervisorId());
                                return sameDept || assigned;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(filteredInterns);
                }
            }

            // For SUPER_ADMIN - return all interns
            return ResponseEntity.ok(allInterns);
        } catch (Exception e) {
            e.printStackTrace();
            // On error, return all interns (for backward compatibility)
            return ResponseEntity.ok(internService.getAllInterns());
        }
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<?> getMyProfile() {
        try {
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
            }

            User currentUser = currentUserOpt.get();
            Optional<InternResponse> internOpt = internService.getInternByEmail(currentUser.getEmail());
            
            if (internOpt.isPresent()) {
                return ResponseEntity.ok(internOpt.get());
            } else {
                System.err.println("❌ Intern profile not found for email: " + currentUser.getEmail());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Intern profile not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<Intern> getInternById(@PathVariable Long id) {
        return internService.getInternById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public Page<InternResponse> searchInterns(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return internService.searchInterns(name, pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public InternResponse createIntern(@Valid @RequestBody InternRequest request) {
        System.out.println("✓ Creating new intern:");
        System.out.println("  Name: " + request.getName());
        System.out.println("  Email: " + request.getEmail());
        System.out.println("  Department ID: " + request.getDepartmentId());
        System.out.println("  Supervisor ID: " + request.getSupervisorId());

        InternResponse response = internService.createIntern(request);

        System.out.println("✓ Intern created and saved to database:");
        System.out.println("  Intern ID: " + response.getId());
        System.out.println("  Name: " + response.getName());
        System.out.println("  Department: " + response.getDepartmentName());

        // Broadcast real-time update
        webSocketService.broadcastInternUpdate("CREATED", response);

        // Log intern creation
        securityUtil.getCurrentUser().ifPresent(currentUser -> {
            activityLogService.log(currentUser.getUsername(), "CREATE_INTERN",
                    "Created intern: " + response.getEmail() + " (" + response.getName() + ")", null);
        });

        return response;
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> bulkCreateInterns(@Valid @RequestBody BulkInternRequest bulkRequest) {
        try {
            List<InternResponse> responses = new java.util.ArrayList<>();
            List<String> errors = new java.util.ArrayList<>();
            int successCount = 0;

            String defaultPassword = bulkRequest.getDefaultPassword();
            if (defaultPassword == null || defaultPassword.trim().isEmpty()) {
                defaultPassword = "Password123!"; // Fallback default
            }

            if (bulkRequest.getInterns() == null || bulkRequest.getInterns().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No interns provided for bulk import"));
            }

            for (InternRequest request : bulkRequest.getInterns()) {
                try {
                    String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "unknown";

                    // Validate email domain
                    if (!email.endsWith("@univen.ac.za")) {
                        errors.add("Invalid email domain for " + email + ". Only @univen.ac.za is allowed.");
                        continue;
                    }

                    // Call the transactional service method
                    InternResponse response = internService.createInternWithUser(request, defaultPassword);

                    responses.add(response);
                    successCount++;

                    // Broadcast real-time update
                    webSocketService.broadcastInternUpdate("CREATED", response);

                    // Send invitation email if requested
                    if (Boolean.TRUE.equals(bulkRequest.getSendInvites())) {
                        emailService.sendInternInvite(email, response.getName(), defaultPassword);
                    }

                } catch (Exception e) {
                    String email = (request != null && request.getEmail() != null) ? request.getEmail() : "unknown";
                    errors.add("Failed to import " + email + ": " + e.getMessage());
                    System.err.println("❌ Bulk import row failure for " + email + ": " + e.getMessage());
                }
            }

            // Log bulk intern creation
            if (successCount > 0) {
                final int count = successCount;
                securityUtil.getCurrentUser().ifPresent(currentUser -> {
                    activityLogService.log(currentUser.getUsername(), "BULK_CREATE_INTERN",
                            "Bulk imported " + count + " interns", null);
                });
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("message", "Successfully imported " + successCount + " interns.");
            result.put("successCount", successCount);
            result.put("interns", responses);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process bulk import: " + e.getMessage()));
        }
    }

    @PostMapping("/bulk-invite")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> bulkSendInvites(@Valid @RequestBody BulkInternInviteRequest inviteRequest) {
        try {
            if (inviteRequest.getInvites() == null || inviteRequest.getInvites().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No invites provided"));
            }

            int successCount = 0;
            for (BulkInternInviteRequest.InternInviteData data : inviteRequest.getInvites()) {
                try {
                    emailService.sendInternInviteWithCustomMessage(data.getEmail(), data.getName(), inviteRequest.getMessage());
                    successCount++;
                } catch (Exception e) {
                    System.err.println("❌ Failed to send invite to " + data.getEmail() + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully sent " + successCount + " invitations.",
                    "successCount", successCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send bulk invites: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public InternResponse updateIntern(@PathVariable Long id, @Valid @RequestBody InternRequest request) {
        InternResponse response = internService.updateIntern(id, request);

        // Broadcast real-time update
        webSocketService.broadcastInternUpdate("UPDATED", response);
        webSocketService.broadcastUserUpdate("PROFILE_UPDATED", Map.of(
                "email", response.getEmail(),
                "name", response.getName(),
                "department", response.getDepartmentName() != null ? response.getDepartmentName() : "",
                "role", "INTERN"
        ));

        // Log intern update
        securityUtil.getCurrentUser().ifPresent(currentUser -> {
            activityLogService.log(currentUser.getUsername(), "UPDATE_INTERN",
                    "Updated intern: " + response.getEmail(), null);
        });

        return response;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public void deleteIntern(@PathVariable Long id) {
        internService.deleteIntern(id);

        // Broadcast real-time update
        webSocketService.broadcastInternUpdate("DELETED", Map.of("internId", id));

        // Log intern deletion
        securityUtil.getCurrentUser().ifPresent(currentUser -> {
            activityLogService.log(currentUser.getUsername(), "DELETE_INTERN",
                    "Deleted intern ID: " + id, null);
        });
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> activateIntern(@PathVariable Long id) {
        try {
            InternResponse response = internService.activateIntern(id, true);

            // Broadcast real-time update
            webSocketService.broadcastInternUpdate("ACTIVATED", response);

            // Log activation
            securityUtil.getCurrentUser().ifPresent(currentUser -> {
                activityLogService.log(currentUser.getUsername(), "ACTIVATE_INTERN",
                        "Approved/Activated intern: " + response.getEmail(), null);
            });

            return ResponseEntity.ok(Map.of(
                    "message", "Intern approved and activated successfully",
                    "intern", response));
        } catch (com.internregister.service.NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to activate intern: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> deactivateIntern(@PathVariable Long id) {
        try {
            InternResponse response = internService.activateIntern(id, false);

            // Broadcast real-time update
            webSocketService.broadcastInternUpdate("DEACTIVATED", response);

            // Log deactivation
            securityUtil.getCurrentUser().ifPresent(currentUser -> {
                activityLogService.log(currentUser.getUsername(), "DEACTIVATE_INTERN",
                        "Deactivated intern: " + response.getEmail(), null);
            });

            return ResponseEntity.ok(Map.of(
                    "message", "Intern deactivated successfully",
                    "intern", response));
        } catch (com.internregister.service.NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to deactivate intern: " + e.getMessage()));
        }
    }

    /**
     * Save intern signature (Base64 string → byte[])
     * POST /api/interns/{id}/signature
     */
    @PutMapping("/{id}/signature")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<?> saveInternSignature(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Intern not found"));
            }

            Intern intern = internOpt.get();
            String signatureBase64 = body.get("signature");

            if (signatureBase64 == null || signatureBase64.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Signature is required"));
            }

            // Remove data URL prefix if present (e.g., "data:image/png;base64,")
            String base64Data = signatureBase64;
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            // Convert Base64 string to byte[]
            try {
                byte[] signatureBytes = Base64.getDecoder().decode(base64Data);
                intern.setSignature(signatureBytes);
                internService.updateInternSignature(intern);

                System.out.println(
                        "✅ Signature saved for intern ID: " + id + " (Size: " + signatureBytes.length + " bytes)");

                Map<String, Object> result = Map.of(
                        "message", "Signature saved successfully",
                        "hasSignature", true,
                        "size", signatureBytes.length,
                        "internId", id);

                // Broadcast real-time update
                webSocketService.broadcastInternUpdate("SIGNATURE_UPDATED", result);

                return ResponseEntity.ok(result);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid Base64 signature format"));
            }
        } catch (Exception e) {
            System.err.println("❌ Error saving intern signature: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save signature: " + e.getMessage()));
        }
    }

    /**
     * Get intern signature (byte[] → Base64)
     * GET /api/interns/{id}/signature
     */
    @GetMapping("/{id}/signature")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<?> getInternSignature(@PathVariable Long id) {
        try {
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Intern not found"));
            }

            Intern intern = internOpt.get();
            if (intern.getSignature() == null || intern.getSignature().length == 0) {
                return ResponseEntity.ok(Map.of(
                        "hasSignature", false,
                        "signature", ""));
            }

            // Convert byte[] to Base64 string
            String signatureBase64 = Base64.getEncoder().encodeToString(intern.getSignature());

            return ResponseEntity.ok(Map.of(
                    "hasSignature", true,
                    "signature", "data:image/png;base64," + signatureBase64));
        } catch (Exception e) {
            System.err.println("❌ Error retrieving intern signature: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to retrieve signature: " + e.getMessage()));
        }
    }

    /**
     * Get intern signature as binary image (for direct image display)
     * GET /api/interns/{id}/signature/image
     */
    @GetMapping("/{id}/signature/image")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<byte[]> getInternSignatureImage(@PathVariable Long id) {
        try {
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(404).build();
            }

            Intern intern = internOpt.get();
            if (intern.getSignature() == null || intern.getSignature().length == 0) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(intern.getSignature().length);
            headers.set("Content-Disposition", "inline; filename=\"signature.png\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(intern.getSignature());
        } catch (Exception e) {
            System.err.println("❌ Error retrieving intern signature image: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get intern contract agreement (LONGTEXT base64 string)
     * GET /api/interns/{id}/contract-agreement
     */
    @GetMapping("/{id}/contract-agreement")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<?> getInternContractAgreement(@PathVariable Long id) {
        try {
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Intern not found"));
            }

            String contractAgreement = internService.getContractAgreement(id);
            if (contractAgreement == null || contractAgreement.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "hasContract", false,
                        "contractAgreement", ""));
            }

            return ResponseEntity.ok(Map.of(
                    "hasContract", true,
                    "contractAgreement", contractAgreement));
        } catch (Exception e) {
            System.err.println("❌ Error retrieving intern contract agreement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve contract agreement: " + e.getMessage()));
        }
    }

    /**
     * Update intern contract agreement
     * PUT /api/interns/{id}/contract-agreement
     */
    @PutMapping("/{id}/contract-agreement")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR') or hasRole('INTERN')")
    public ResponseEntity<?> updateInternContractAgreement(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            System.out.println("Processing contract upload for intern ID: " + id);
            
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
            }

            User currentUser = currentUserOpt.get();
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                System.err.println("❌ Intern not found with ID: " + id);
                return ResponseEntity.status(404).body(Map.of("error", "Intern not found"));
            }

            Intern intern = internOpt.get();

            // Security check: Interns can only update their own agreement
            String currentEmail = currentUser.getEmail().toLowerCase().trim();
            String internEmail = intern.getEmail().toLowerCase().trim();
            
            if (currentUser.getRole() == User.Role.INTERN && !currentEmail.equals(internEmail)) {
                System.err.println("❌ Security violation: Intern " + currentEmail + " tried to update contract for " + internEmail);
                return ResponseEntity.status(403).body(Map.of("error", "You can only update your own agreement"));
            }

            String contractAgreement = body.get("contractAgreement");
            if (contractAgreement == null || contractAgreement.trim().isEmpty()) {
                System.err.println("❌ Contract agreement content is missing or empty");
                return ResponseEntity.badRequest().body(Map.of("error", "Contract agreement content is required"));
            }
            
            System.out.println("✓ Valid agreement received (Length: " + contractAgreement.length() + ")");

            InternResponse response = internService.updateContractAgreement(id, contractAgreement);

            // Log update
            activityLogService.log(currentUser.getUsername(), "UPDATE_CONTRACT",
                    "Updated contract for intern: " + response.getEmail(), null);

            System.out.println("✓ Contract updated successfully for: " + internEmail);
            return ResponseEntity.ok(Map.of(
                    "message", "Contract agreement updated successfully",
                    "intern", response));
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Failed to update contract agreement: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update contract agreement: " + e.getMessage()));
        }
    }

    /**
     * Assign location to intern
     * PUT /api/interns/{id}/location
     */
    @PutMapping("/{id}/location")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('SUPERVISOR')")
    public ResponseEntity<?> assignLocationToIntern(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<Intern> internOpt = internService.getInternById(id);
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Intern not found"));
            }

            Object locationIdObj = body.get("locationId");
            Long locationId = null;

            if (locationIdObj != null) {
                if (locationIdObj instanceof Number) {
                    locationId = ((Number) locationIdObj).longValue();
                } else if (locationIdObj instanceof String) {
                    try {
                        locationId = Long.parseLong((String) locationIdObj);
                    } catch (NumberFormatException e) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid locationId format"));
                    }
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid locationId format"));
                }

                // Verify location exists if locationId is provided
                Optional<Location> locationOpt = locationRepository.findById(locationId);
                if (locationOpt.isEmpty()) {
                    return ResponseEntity.status(404).body(Map.of("error", "Location not found"));
                }
            }

            // Assign location using service method
            InternResponse response = internService.assignLocationToIntern(id, locationId);

            System.out.println("✅ Location assigned to intern ID: " + id +
                    (locationId != null ? " (Location ID: " + locationId + ")" : " (Location removed)"));

            Map<String, Object> result = Map.of(
                    "message", locationId != null ? "Location assigned successfully" : "Location removed successfully",
                    "intern", response);

            // Broadcast real-time update
            webSocketService.broadcastInternUpdate("LOCATION_ASSIGNED", result);

            return ResponseEntity.ok(result);
        } catch (com.internregister.service.NotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("❌ Error assigning location to intern: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to assign location: " + e.getMessage()));
        }
    }
}
