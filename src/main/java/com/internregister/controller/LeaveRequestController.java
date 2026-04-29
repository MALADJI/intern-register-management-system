package com.internregister.controller;

import com.internregister.entity.LeaveRequest;
import com.internregister.entity.User;
import com.internregister.entity.Intern;
import com.internregister.entity.Supervisor;
import com.internregister.service.LeaveRequestService;
import com.internregister.service.WebSocketService;
import com.internregister.util.SecurityUtil;
import com.internregister.repository.InternRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.service.ActivityLogService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import com.internregister.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/leave")
@CrossOrigin(origins = "*")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final SecurityUtil securityUtil;
    private final InternRepository internRepository;
    private final SupervisorRepository supervisorRepository;
    private final WebSocketService webSocketService;
    private final ActivityLogService activityLogService;

    @Autowired
    private FileStorageService fileStorageService;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
            SecurityUtil securityUtil,
            InternRepository internRepository,
            SupervisorRepository supervisorRepository,
            WebSocketService webSocketService,
            ActivityLogService activityLogService) {
        this.leaveRequestService = leaveRequestService;
        this.securityUtil = securityUtil;
        this.internRepository = internRepository;
        this.supervisorRepository = supervisorRepository;
        this.webSocketService = webSocketService;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getAllLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId) {
        try {
            // Get current authenticated user
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            System.out.println("🔍 [getAllLeaveRequests] Authentication check:");
            System.out.println("   - Auth object: " + (auth != null ? "present" : "null"));
            if (auth != null) {
                System.out.println("   - Authenticated: " + auth.isAuthenticated());
                System.out.println("   - Principal: " + auth.getPrincipal());
                System.out.println("   - Name: " + auth.getName());
                System.out.println("   - Authorities: " + auth.getAuthorities());
            }

            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                // Check authentication context for debugging
                if (auth == null || !auth.isAuthenticated() ||
                        "anonymousUser".equals(auth.getPrincipal())) {
                    System.out.println("❌ getAllLeaveRequests: User not authenticated");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "User not authenticated. Please log in again."));
                } else {
                    String authName = auth.getName();
                    System.out.println("❌ getAllLeaveRequests: User authenticated but not found in database.");
                    System.out.println("   - Authentication name: " + authName);
                    System.out.println("   - Attempted lookup by username: " + authName);
                    System.out.println("   - Attempted lookup by email: " + authName);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of(
                                    "error", "User not found in database. Please contact administrator.",
                                    "details", "Authenticated as: " + authName + " but user not found in database"));
                }
            }

            User currentUser = currentUserOpt.get();
            System.out.println("✓ getAllLeaveRequests: User authenticated - " + currentUser.getUsername() + " (Role: "
                    + currentUser.getRole() + ", Active: " + currentUser.getActive() + ")");

            // Check if user is active
            if (currentUser.getActive() == null || !currentUser.getActive()) {
                System.out.println("❌ getAllLeaveRequests: User is inactive");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Your account is inactive. Please contact administrator."));
            }

            // If user is INTERN, return only their leave requests
            if (currentUser.getRole() == User.Role.INTERN) {
                Optional<Intern> internOpt = internRepository.findByEmail(currentUser.getEmail()).stream().findFirst();
                if (internOpt.isEmpty()) {
                    return ResponseEntity.ok(List.of()); // Return empty list if intern profile not found
                }

                Intern intern = internOpt.get();
                List<LeaveRequest> requests = leaveRequestService.getLeaveRequestsByIntern(intern.getInternId());

                // Filter by status if provided
                if (status != null && !status.isEmpty()) {
                    requests = requests.stream()
                            .filter(req -> req.getStatus() != null && status.equalsIgnoreCase(req.getStatus().name()))
                            .collect(java.util.stream.Collectors.toList());
                }

                // Map to DTO format to avoid lazy loading issues and include intern information
                List<Map<String, Object>> responseList = requests.stream().map(req -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("requestId", req.getRequestId());
                    map.put("id", req.getRequestId());
                    map.put("leaveType", req.getLeaveType() != null ? req.getLeaveType().name() : null);
                    map.put("fromDate", req.getFromDate() != null ? req.getFromDate().toString() : null);
                    map.put("toDate", req.getToDate() != null ? req.getToDate().toString() : null);
                    map.put("startDate", req.getFromDate() != null ? req.getFromDate().toString() : null);
                    map.put("endDate", req.getToDate() != null ? req.getToDate().toString() : null);
                    map.put("status", req.getStatus() != null ? req.getStatus().name() : null);
                    map.put("attachmentPath", req.getAttachmentPath());
                    map.put("document", req.getAttachmentPath());
                    map.put("reason", req.getReason());
                    map.put("createdAt", req.getCreatedAt());
                    map.put("updatedAt", req.getUpdatedAt());
                    map.put("viewedBySupervisor", req.isViewedBySupervisor());
                    if (req.getIntern() != null) {
                        map.put("internId", req.getIntern().getInternId());
                        map.put("name", req.getIntern().getName());
                        map.put("email", req.getIntern().getEmail());
                        map.put("field", req.getIntern().getField());
                    }
                    return map;
                }).collect(java.util.stream.Collectors.toList());

                return ResponseEntity.ok(responseList);
            }

            // For ADMIN, SUPERVISOR, SUPER_ADMIN - return requests (filtered appropriately)
            List<LeaveRequest> requests;
            System.out.println("🔍 Getting leave requests for role: " + currentUser.getRole());

            // ✅ For SUPERVISOR: Only return leave requests from interns assigned to this
            // supervisor
            if (currentUser.getRole() == User.Role.SUPERVISOR) {
                System.out.println("  Supervisor detected - filtering by assigned interns");
                Optional<Supervisor> supervisorOpt = supervisorRepository.findByEmail(currentUser.getEmail()).stream().findFirst();
                if (supervisorOpt.isEmpty()) {
                    System.err.println("❌ Supervisor profile not found for email: " + currentUser.getEmail());
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Supervisor profile not found"));
                }

                Supervisor supervisor = supervisorOpt.get();
                System.out.println(
                        "✓ Found supervisor: " + supervisor.getName() + " (ID: " + supervisor.getSupervisorId() + ")");

                // Get all leave requests
                try {
                    if (status != null && !status.isEmpty()) {
                        System.out.println("  Filtering by status: " + status);
                        requests = leaveRequestService.getLeaveRequestsByStatus(status);
                    } else {
                        System.out.println("  Getting all leave requests");
                        requests = leaveRequestService.getAllLeaveRequests();
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error getting leave requests from service: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }

                // Filter to only include leave requests from interns assigned to this
                // supervisor
                // Filter to include requests from interns assigned to this supervisor OR in the
                // same department
                final Long supervisorId = supervisor.getSupervisorId();
                final Long supervisorDeptId = supervisor.getDepartment() != null
                        ? supervisor.getDepartment().getDepartmentId()
                        : null;

                requests = requests.stream()
                        .filter(req -> {
                            // Check 1: Intern exists
                            if (req.getIntern() == null)
                                return false;

                            // Check 2: Explicit Supervisor Assignment
                            boolean isAssigned = req.getIntern().getSupervisor() != null
                                    && req.getIntern().getSupervisor().getSupervisorId().equals(supervisorId);

                            // Check 3: Same Department (fallback)
                            boolean isSameDept = supervisorDeptId != null
                                    && req.getIntern().getDepartment() != null
                                    && req.getIntern().getDepartment().getDepartmentId().equals(supervisorDeptId);

                            return isAssigned || isSameDept;
                        })
                        .collect(java.util.stream.Collectors.toList());
                System.out.println("✓ Filtered to " + requests.size() + " leave request(s) for supervisor's interns");
            } else {
                // For ADMIN and SUPER_ADMIN - return all requests (filtered by department if
                // provided)
                try {
                    if (status != null && !status.isEmpty()) {
                        System.out.println("  Filtering by status: " + status);
                        requests = leaveRequestService.getLeaveRequestsByStatus(status);
                    } else {
                        System.out.println("  Getting all leave requests");
                        requests = leaveRequestService.getAllLeaveRequests();
                    }
                    System.out.println("✓ Found " + requests.size() + " leave request(s)");
                } catch (Exception e) {
                    System.err.println("❌ Error getting leave requests from service: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }

                // ✅ Filter by department if departmentId is provided (for ADMIN)
                if (departmentId != null && currentUser.getRole() == User.Role.ADMIN) {
                    requests = requests.stream()
                            .filter(req -> req.getIntern() != null
                                    && req.getIntern().getDepartment() != null
                                    && req.getIntern().getDepartment().getDepartmentId().equals(departmentId))
                            .collect(java.util.stream.Collectors.toList());
                }
            }

            // Map to DTO format to avoid lazy loading issues and include intern information
            List<Map<String, Object>> responseList = requests.stream().map(req -> {
                try {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("requestId", req.getRequestId());
                    map.put("id", req.getRequestId()); // Also include as 'id' for frontend compatibility
                    map.put("leaveType", req.getLeaveType() != null ? req.getLeaveType().name() : null);
                    map.put("fromDate", req.getFromDate() != null ? req.getFromDate().toString() : null);
                    map.put("toDate", req.getToDate() != null ? req.getToDate().toString() : null);
                    map.put("startDate", req.getFromDate() != null ? req.getFromDate().toString() : null); // Frontend
                                                                                                           // compatibility
                    map.put("endDate", req.getToDate() != null ? req.getToDate().toString() : null); // Frontend
                                                                                                     // compatibility
                    map.put("status", req.getStatus() != null ? req.getStatus().name() : null);
                    map.put("attachmentPath", req.getAttachmentPath());
                    map.put("document", req.getAttachmentPath()); // Frontend compatibility
                    map.put("reason", req.getReason());
                    map.put("createdAt", req.getCreatedAt());
                    map.put("updatedAt", req.getUpdatedAt());
                    map.put("viewedBySupervisor", req.isViewedBySupervisor());
                    // Include intern information if available
                    if (req.getIntern() != null) {
                        map.put("internId", req.getIntern().getInternId());
                        map.put("internName", req.getIntern().getName());
                        map.put("internEmail", req.getIntern().getEmail());
                        map.put("field", req.getIntern().getField());
                        map.put("name", req.getIntern().getName()); // Frontend compatibility
                        map.put("email", req.getIntern().getEmail()); // Frontend compatibility

                        // ✅ Include department information
                        if (req.getIntern().getDepartment() != null) {
                            map.put("department", req.getIntern().getDepartment().getName());
                            map.put("departmentId", req.getIntern().getDepartment().getDepartmentId());
                        }
                    }
                    return map;
                } catch (Exception e) {
                    System.err.println("Error mapping leave request " + req.getRequestId() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Return a minimal map to avoid breaking the entire response
                    Map<String, Object> errorMap = new java.util.HashMap<>();
                    errorMap.put("requestId", req.getRequestId());
                    errorMap.put("id", req.getRequestId());
                    errorMap.put("error", "Error processing request: " + e.getMessage());
                    return errorMap;
                }
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            System.err.println("❌ ERROR in getAllLeaveRequests:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            // Include more details in response for debugging
            String errorDetails = e.getMessage();
            if (e.getCause() != null) {
                errorDetails += " | Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to retrieve leave requests: " + errorDetails,
                            "errorType", e.getClass().getSimpleName()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequest> getLeaveRequestById(@PathVariable Long id) {
        if (id == null) return ResponseEntity.badRequest().build();
        LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(id);
        if (leaveRequest != null) {
            return ResponseEntity.ok(leaveRequest);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/intern/{internId}")
    public List<LeaveRequest> getLeaveRequestsByIntern(@PathVariable Long internId) {
        if (internId == null) return java.util.Collections.emptyList();
        return leaveRequestService.getLeaveRequestsByIntern(internId);
    }

    @GetMapping("/my-leave")
    public ResponseEntity<?> getMyLeaveRequests() {
        try {
            // Get current authenticated user
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            User currentUser = currentUserOpt.get();

            // Only allow INTERN role to use this endpoint
            if (currentUser.getRole() != User.Role.INTERN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "This endpoint is only available for interns"));
            }

            // Find intern profile by email
            Optional<Intern> internOpt = internRepository.findByEmail(currentUser.getEmail()).stream().findFirst();
            if (internOpt.isEmpty()) {
                return ResponseEntity.ok(List.of()); // Return empty list if intern profile not found
            }

            Intern intern = internOpt.get();
            List<LeaveRequest> requests = leaveRequestService.getLeaveRequestsByIntern(intern.getInternId());

            // Map to include intern information in response
            List<Map<String, Object>> responseList = requests.stream().map(req -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("requestId", req.getRequestId());
                map.put("id", req.getRequestId()); // Also include as 'id' for frontend compatibility
                map.put("leaveType", req.getLeaveType() != null ? req.getLeaveType().name() : null);
                map.put("fromDate", req.getFromDate() != null ? req.getFromDate().toString() : null);
                map.put("toDate", req.getToDate() != null ? req.getToDate().toString() : null);
                map.put("startDate", req.getFromDate() != null ? req.getFromDate().toString() : null); // Frontend
                                                                                                       // compatibility
                map.put("endDate", req.getToDate() != null ? req.getToDate().toString() : null); // Frontend
                                                                                                 // compatibility
                map.put("status", req.getStatus() != null ? req.getStatus().name() : null);
                map.put("attachmentPath", req.getAttachmentPath());
                map.put("document", req.getAttachmentPath()); // Frontend compatibility
                map.put("reason", req.getReason());
                map.put("createdAt", req.getCreatedAt());
                map.put("updatedAt", req.getUpdatedAt());
                map.put("viewedBySupervisor", req.isViewedBySupervisor());
                // Include intern information
                if (req.getIntern() != null) {
                    map.put("internId", req.getIntern().getInternId());
                    map.put("name", req.getIntern().getName());
                    map.put("email", req.getIntern().getEmail());
                    map.put("field", req.getIntern().getField());
                }
                return map;
            }).collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve leave requests: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public Page<LeaveRequest> searchLeaveRequests(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long internId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fromDate").descending());
        return leaveRequestService.searchLeaveRequests(status, internId, pageable);
    }

    @PostMapping
    public ResponseEntity<?> submitLeaveRequest(@RequestBody Map<String, Object> body) {
        try {
            // Get current authenticated user
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            User currentUser = currentUserOpt.get();

            // Only allow INTERN role to submit leave requests
            if (currentUser.getRole() != User.Role.INTERN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only interns can submit leave requests"));
            }

            // Find intern profile by email
            Optional<Intern> internOpt = internRepository.findByEmail(currentUser.getEmail()).stream().findFirst();
            if (internOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Intern profile not found"));
            }

            Intern intern = internOpt.get();

            // Create leave request from body
            LeaveRequest leaveRequest = new LeaveRequest();
            leaveRequest.setIntern(intern);

            // Set leave type
            Object leaveTypeObj = body.get("leaveType");
            if (leaveTypeObj != null) {
                try {
                    leaveRequest.setLeaveType(com.internregister.entity.LeaveType.valueOf(leaveTypeObj.toString()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid leave type: " + leaveTypeObj));
                }
            }

            // Set dates
            Object fromDateObj = body.get("fromDate");
            if (fromDateObj != null) {
                try {
                    leaveRequest.setFromDate(java.time.LocalDate.parse(fromDateObj.toString()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid fromDate format: " + fromDateObj));
                }
            }

            Object toDateObj = body.get("toDate");
            if (toDateObj != null) {
                try {
                    leaveRequest.setToDate(java.time.LocalDate.parse(toDateObj.toString()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid toDate format: " + toDateObj));
                }
            }

            // Set reason if provided
            Object reasonObj = body.get("reason");
            if (reasonObj != null) {
                leaveRequest.setReason(reasonObj.toString());
            }

            // Validate dates
            if (leaveRequest.getFromDate() != null && leaveRequest.getToDate() != null) {
                if (leaveRequest.getToDate().isBefore(leaveRequest.getFromDate())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "End date must be after start date"));
                }
            }

            // Validate required fields
            if (leaveRequest.getLeaveType() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Leave type is required"));
            }
            if (leaveRequest.getFromDate() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Start date is required"));
            }
            if (leaveRequest.getToDate() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "End date is required"));
            }

            // Note: Document attachment is validated on frontend and must be uploaded via
            // /{id}/attachment endpoint
            // The frontend should ensure attachment is provided before submitting the
            // request

            LeaveRequest saved = leaveRequestService.submitLeaveRequest(leaveRequest);

            // Broadcast real-time update
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("requestId", saved.getRequestId());
            broadcastData.put("id", saved.getRequestId());
            broadcastData.put("leaveType", saved.getLeaveType() != null ? saved.getLeaveType().name() : null);
            broadcastData.put("fromDate", saved.getFromDate() != null ? saved.getFromDate().toString() : null);
            broadcastData.put("toDate", saved.getToDate() != null ? saved.getToDate().toString() : null);
            broadcastData.put("status", saved.getStatus() != null ? saved.getStatus().name() : null);
            broadcastData.put("internId", saved.getIntern() != null ? saved.getIntern().getInternId() : null);
            if (saved.getIntern() != null) {
                broadcastData.put("internName", saved.getIntern().getName());
                broadcastData.put("internEmail", saved.getIntern().getEmail());
            }
            webSocketService.broadcastLeaveRequestUpdate("CREATED", broadcastData);

            // Log leave submission
            activityLogService.log(currentUser.getUsername(), "SUBMIT_LEAVE",
                    "Submitted leave request: " + saved.getLeaveType() + " from " + saved.getFromDate() + " to "
                            + saved.getToDate(),
                    null);

            // Return formatted response matching getMyLeaveRequests format
            Map<String, Object> responseMap = new java.util.HashMap<>();
            responseMap.put("requestId", saved.getRequestId());
            responseMap.put("id", saved.getRequestId()); // Also include as 'id' for frontend compatibility
            responseMap.put("leaveType", saved.getLeaveType() != null ? saved.getLeaveType().name() : null);
            responseMap.put("fromDate", saved.getFromDate() != null ? saved.getFromDate().toString() : null);
            responseMap.put("toDate", saved.getToDate() != null ? saved.getToDate().toString() : null);
            responseMap.put("startDate", saved.getFromDate() != null ? saved.getFromDate().toString() : null); // Frontend
                                                                                                               // compatibility
            responseMap.put("endDate", saved.getToDate() != null ? saved.getToDate().toString() : null); // Frontend
                                                                                                         // compatibility
            responseMap.put("status", saved.getStatus() != null ? saved.getStatus().name() : null);
            responseMap.put("attachmentPath", saved.getAttachmentPath());
            responseMap.put("document", saved.getAttachmentPath()); // Frontend compatibility
            responseMap.put("reason", saved.getReason());
            responseMap.put("createdAt", saved.getCreatedAt());
            responseMap.put("updatedAt", saved.getUpdatedAt());
            responseMap.put("viewedBySupervisor", saved.isViewedBySupervisor());
            // Include intern information
            if (saved.getIntern() != null) {
                responseMap.put("internId", saved.getIntern().getInternId());
                responseMap.put("name", saved.getIntern().getName());
                responseMap.put("email", saved.getIntern().getEmail());
            }

            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit leave request: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLeaveRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            // Get current user
            Optional<User> currentUserOpt = securityUtil.getCurrentUser();
            if (currentUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            User currentUser = currentUserOpt.get();

            // Only allow INTERN role to update leave requests
            if (currentUser.getRole() != User.Role.INTERN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only interns can update leave requests"));
            }

            // Find the leave request
            if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "ID is required"));
            LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(id);
            if (leaveRequest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Leave request not found"));
            }

            // Verify ownership - only the intern who created the request can edit it
            if (leaveRequest.getIntern() == null ||
                    !leaveRequest.getIntern().getEmail().equalsIgnoreCase(currentUser.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only edit your own leave requests"));
            }

            // Only allow editing of PENDING requests
            if (leaveRequest.getStatus() != com.internregister.entity.LeaveStatus.PENDING) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Only pending leave requests can be edited"));
            }

            // Update leave type if provided
            Object leaveTypeObj = body.get("leaveType");
            if (leaveTypeObj != null) {
                try {
                    leaveRequest.setLeaveType(com.internregister.entity.LeaveType.valueOf(leaveTypeObj.toString()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid leave type: " + leaveTypeObj));
                }
            }

            // Update dates if provided
            Object fromDateObj = body.get("fromDate");
            if (fromDateObj != null) {
                try {
                    leaveRequest.setFromDate(java.time.LocalDate.parse(fromDateObj.toString()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid fromDate format: " + fromDateObj));
                }
            }

            Object toDateObj = body.get("toDate");
            if (toDateObj != null) {
                try {
                    leaveRequest.setToDate(java.time.LocalDate.parse(toDateObj.toString()));
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid toDate format: " + toDateObj));
                }
            }

            // Update reason if provided
            Object reasonObj = body.get("reason");
            if (reasonObj != null) {
                leaveRequest.setReason(reasonObj.toString());
            }

            // Save updated request
            LeaveRequest updated = leaveRequestService.updateLeaveRequest(leaveRequest);

            // Broadcast WebSocket update
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("requestId", updated.getRequestId());
            broadcastData.put("id", updated.getRequestId());
            broadcastData.put("status", updated.getStatus() != null ? updated.getStatus().name() : null);
            if (updated.getIntern() != null) {
                broadcastData.put("internId", updated.getIntern().getInternId());
                broadcastData.put("internEmail", updated.getIntern().getEmail());
            }
            webSocketService.broadcastLeaveRequestUpdate("UPDATED", broadcastData);

            // Log leave update
            activityLogService.log(currentUser.getUsername(), "UPDATE_LEAVE",
                    "Updated leave request: " + updated.getLeaveType() + " from " + updated.getFromDate() + " to "
                            + updated.getToDate(),
                    null);

            // Return formatted response
            Map<String, Object> responseMap = new java.util.HashMap<>();
            responseMap.put("requestId", updated.getRequestId());
            responseMap.put("id", updated.getRequestId());
            responseMap.put("leaveType", updated.getLeaveType() != null ? updated.getLeaveType().name() : null);
            responseMap.put("fromDate", updated.getFromDate() != null ? updated.getFromDate().toString() : null);
            responseMap.put("toDate", updated.getToDate() != null ? updated.getToDate().toString() : null);
            responseMap.put("startDate", updated.getFromDate() != null ? updated.getFromDate().toString() : null);
            responseMap.put("endDate", updated.getToDate() != null ? updated.getToDate().toString() : null);
            responseMap.put("status", updated.getStatus() != null ? updated.getStatus().name() : null);
            responseMap.put("attachmentPath", updated.getAttachmentPath());
            responseMap.put("document", updated.getAttachmentPath());
            responseMap.put("reason", updated.getReason());
            responseMap.put("createdAt", updated.getCreatedAt());
            responseMap.put("updatedAt", updated.getUpdatedAt());
            responseMap.put("viewedBySupervisor", updated.isViewedBySupervisor());
            if (updated.getIntern() != null) {
                responseMap.put("internId", updated.getIntern().getInternId());
                responseMap.put("name", updated.getIntern().getName());
                responseMap.put("email", updated.getIntern().getEmail());
            }

            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update leave request: " + e.getMessage()));
        }
    }

    @PostMapping(path = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "ID is required"));
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "File is required"));
            }

            LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(id);
            if (leaveRequest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Leave request not found"));
            }
            String filename = fileStorageService.saveFile(file);
            leaveRequest.setAttachmentPath(filename);
            leaveRequestService.save(leaveRequest);

            // Return JSON response
            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded successfully",
                    "filename", filename,
                    "attachmentPath", filename));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error uploading file: " + e.getMessage()));
        }
    }

    @GetMapping("/attachment/{filename}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String filename) {
        try {
            Resource resource = fileStorageService.loadFile(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<LeaveRequest> approveLeave(@PathVariable Long id) {
        if (id == null) return ResponseEntity.badRequest().build();
        LeaveRequest approved = leaveRequestService.approveLeave(id);

        // Broadcast real-time update
        if (approved != null) {
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("requestId", approved.getRequestId());
            broadcastData.put("id", approved.getRequestId());
            broadcastData.put("status", approved.getStatus() != null ? approved.getStatus().name() : null);
            broadcastData.put("internId", approved.getIntern() != null ? approved.getIntern().getInternId() : null);
            webSocketService.broadcastLeaveRequestUpdate("APPROVED", broadcastData);

            // Log leave approval
            securityUtil.getCurrentUser()
                    .ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(), "APPROVE_LEAVE",
                            "Approved leave request for: "
                                    + (approved.getIntern() != null ? approved.getIntern().getEmail() : "Unknown"),
                            null));
        }

        return ResponseEntity.ok(approved);
    }

    @PutMapping("/reject/{id}")
    public ResponseEntity<?> rejectLeave(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "ID is required"));
        String reason = body != null ? body.get("reason") : null;
        LeaveRequest leaveRequest = leaveRequestService.rejectLeave(id, reason);
        if (leaveRequest != null) {
            // Broadcast real-time update
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("requestId", leaveRequest.getRequestId());
            broadcastData.put("id", leaveRequest.getRequestId());
            broadcastData.put("status", leaveRequest.getStatus() != null ? leaveRequest.getStatus().name() : null);
            broadcastData.put("internId",
                    leaveRequest.getIntern() != null ? leaveRequest.getIntern().getInternId() : null);
            broadcastData.put("reason", reason);
            webSocketService.broadcastLeaveRequestUpdate("REJECTED", broadcastData);

            // Log leave rejection
            securityUtil.getCurrentUser().ifPresent(currentUser -> activityLogService.log(currentUser.getUsername(),
                    "REJECT_LEAVE",
                    "Rejected leave request for: "
                            + (leaveRequest.getIntern() != null ? leaveRequest.getIntern().getEmail() : "Unknown") +
                            (reason != null ? ". Reason: " + reason : ""),
                    null));

            // If reason is provided, you might want to store it in the LeaveRequest entity
            // For now, just return the rejected leave request
            return ResponseEntity.ok(leaveRequest);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Mark a leave request as seen by the supervisor
     */
    @PutMapping("/{id}/seen")
    public ResponseEntity<?> markAsSeen(@PathVariable Long id) {
        try {
            LeaveRequest leaveRequest = leaveRequestService.getLeaveRequestById(id);
            if (leaveRequest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Leave request not found"));
            }
            leaveRequest.setViewedBySupervisor(true);
            leaveRequestService.save(leaveRequest);
            return ResponseEntity.ok(Map.of("message", "Leave request marked as seen"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark as seen: " + e.getMessage()));
        }
    }

    /**
     * Test alert endpoint - returns a test notification
     */
}
