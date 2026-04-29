package com.internregister.service;

import com.internregister.entity.LeaveRequest;
import com.internregister.entity.LeaveStatus;
import com.internregister.repository.LeaveRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final com.internregister.repository.AttendanceRepository attendanceRepository;
    private final WebSocketService webSocketService;
    private final NotificationService notificationService;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
            com.internregister.repository.AttendanceRepository attendanceRepository,
            WebSocketService webSocketService,
            NotificationService notificationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.webSocketService = webSocketService;
        this.notificationService = notificationService;
    }

    public List<LeaveRequest> getLeaveRequestsByIntern(Long internId) {
        if (internId == null) return java.util.Collections.emptyList();
        return leaveRequestRepository.findByIntern_InternId(internId);
    }

    public LeaveRequest submitLeaveRequest(LeaveRequest leaveRequest) {
        leaveRequest.setStatus(LeaveStatus.PENDING);
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        // Notify Admins
        String internName = leaveRequest.getIntern() != null ? leaveRequest.getIntern().getName() : "An intern";
        String message = internName + " has submitted a new leave request.";
        String link = "/admin/admin-dashboard?section=leave-requests"; // Admin link
        notificationService.notifyAdmins(message, link, "LEAVE_" + saved.getRequestId());
        
        // Notify Super Admins
        String superAdminLink = "/super-admin/dashboard?section=leave-requests"; // Adjust link as needed
        notificationService.notifySuperAdmins(message, superAdminLink, "LEAVE_" + saved.getRequestId());

        // Notify Supervisor
        if (leaveRequest.getIntern() != null && leaveRequest.getIntern().getSupervisor() != null) {
            java.util.List<com.internregister.entity.Supervisor> supervisors = new java.util.ArrayList<>();
            supervisors.add(leaveRequest.getIntern().getSupervisor());
            String supervisorLink = "/supervisor/supervisor-dashboard?section=leave-requests";
            notificationService.notifySupervisors(supervisors, message, supervisorLink,
                    "LEAVE_" + saved.getRequestId());
        }

        return saved;
    }

    @Transactional
    public LeaveRequest updateLeaveRequest(LeaveRequest leaveRequest) {
        leaveRequest.setUpdatedAt(java.time.LocalDateTime.now());
        return leaveRequestRepository.save(leaveRequest);
    }

    public LeaveRequest approveLeave(Long requestId) {
        if (requestId == null) throw new IllegalArgumentException("Request ID is required");
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        request.setStatus(LeaveStatus.APPROVED);
        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        // Notify Intern
        if (request.getIntern() != null) {
            String message = "Your leave request for " + request.getFromDate() + " has been approved.";
            String link = "/intern/intern-dashboard?section=leave-requests";
            notificationService.notifyUserByEmail(request.getIntern().getEmail(), message, "SUCCESS", link,
                    "LEAVE_" + savedRequest.getRequestId());
        }

        // Check if the leave covers TODAY. If so, update attendance immediately.
        java.time.LocalDate today = java.time.LocalDate.now();
        if (request.getFromDate() != null && request.getToDate() != null) {
            if (!today.isBefore(request.getFromDate()) && !today.isAfter(request.getToDate())) {
                System.out.println("✅ [LeaveRequestService] Leave approved for TODAY for intern: "
                        + request.getIntern().getName());

                // Check if attendance record exists for today
                java.time.LocalDateTime startOfDay = today.atStartOfDay();
                java.time.LocalDateTime endOfDay = today.atTime(java.time.LocalTime.MAX);

                List<com.internregister.entity.Attendance> attendances = attendanceRepository
                        .findByInternAndDateBetween(
                                request.getIntern(), startOfDay, endOfDay);

                com.internregister.entity.Attendance attendance;
                if (!attendances.isEmpty()) {
                    attendance = attendances.get(0);
                    // Don't overwrite if they are already PRESENT or SIGNED_IN unless specific
                    // business rule?
                    // Usually APPROVED leave overrides everything.
                    attendance.setStatus(com.internregister.entity.AttendanceStatus.ON_LEAVE);
                } else {
                    attendance = new com.internregister.entity.Attendance();
                    attendance.setIntern(request.getIntern());
                    attendance.setDate(java.time.LocalDateTime.now());
                    attendance.setStatus(com.internregister.entity.AttendanceStatus.ON_LEAVE);
                    attendance.setLocation("Remote/Leave");
                }
                com.internregister.entity.Attendance savedAttendance = attendanceRepository.save(attendance);

                // Broadcast Attendance Update
                java.util.Map<String, Object> broadcastData = new java.util.HashMap<>();
                broadcastData.put("attendanceId", savedAttendance.getAttendanceId());
                broadcastData.put("internId", savedAttendance.getIntern().getInternId());
                broadcastData.put("status", "ON_LEAVE");
                broadcastData.put("propertyName", "status");
                broadcastData.put("newValue", "On Leave");
                if (savedAttendance.getIntern() != null) {
                    broadcastData.put("internName", savedAttendance.getIntern().getName());
                }
                webSocketService.broadcastAttendanceUpdate("UPDATED", broadcastData);
            }
        }

        return savedRequest;
    }

    public LeaveRequest rejectLeave(Long requestId, String reason) {
        if (requestId == null) throw new IllegalArgumentException("Request ID is required");
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));
        request.setStatus(LeaveStatus.REJECTED);
        if (reason != null && !reason.trim().isEmpty()) {
            request.setReason(reason);
        }
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify Intern
        if (saved.getIntern() != null) {
            String message = "Your leave request for " + request.getFromDate() + " has been rejected."
                    + (reason != null ? " Reason: " + reason : "");
            String link = "/intern/intern-dashboard?section=leave-requests";
            notificationService.notifyUserByEmail(saved.getIntern().getEmail(), message, "ERROR", link,
                    "LEAVE_" + saved.getRequestId());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAllLeaveRequests() {
        try {
            System.out.println("🔍 [Service] Calling findAll() on repository...");
            List<LeaveRequest> requests = leaveRequestRepository.findAll();
            System.out.println("✓ [Service] Repository returned " + requests.size() + " leave request(s)");
            if (!requests.isEmpty()) {
                System.out.println("  [Service] First request ID: " + requests.get(0).getRequestId());
                if (requests.get(0).getIntern() != null) {
                    System.out
                            .println("  [Service] First request intern: " + requests.get(0).getIntern().getInternId());
                }
            }
            return requests;
        } catch (IllegalArgumentException e) {
            // Handle enum mismatch errors (e.g., CASUAL, OTHER not in enum)
            if (e.getMessage() != null && e.getMessage().contains("No enum constant")) {
                System.err.println("❌ [Service] Enum mismatch error - database has invalid leave type value");
                System.err.println("  [Service] Error: " + e.getMessage());
                System.err.println(
                        "  [Service] This usually means the database has a leave type that doesn't exist in the enum");
                System.err.println(
                        "  [Service] Please check the leave_requests table and update invalid leave_type values");
                System.err.println("  [Service] Valid enum values: ANNUAL, SICK, PERSONAL, EMERGENCY, CASUAL, OTHER");
                // Return empty list instead of crashing
                return java.util.Collections.emptyList();
            }
            throw e;
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException e) {
            // Handle Hibernate enum conversion errors
            if (e.getCause() instanceof IllegalArgumentException) {
                IllegalArgumentException iae = (IllegalArgumentException) e.getCause();
                if (iae.getMessage() != null && iae.getMessage().contains("No enum constant")) {
                    System.err.println("❌ [Service] Enum mismatch error - database has invalid leave type value");
                    System.err.println("  [Service] Error: " + iae.getMessage());
                    System.err.println(
                            "  [Service] This usually means the database has a leave type that doesn't exist in the enum");
                    System.err.println(
                            "  [Service] Please check the leave_requests table and update invalid leave_type values");
                    System.err
                            .println("  [Service] Valid enum values: ANNUAL, SICK, PERSONAL, EMERGENCY, CASUAL, OTHER");
                    // Return empty list instead of crashing
                    return java.util.Collections.emptyList();
                }
            }
            throw e;
        } catch (Exception e) {
            System.err.println("❌ [Service] Error in getAllLeaveRequests: " + e.getMessage());
            System.err.println("  [Service] Error type: " + e.getClass().getName());
            e.printStackTrace();
            throw e;
        }
    }

    public List<LeaveRequest> getLeaveRequestsByStatus(String status) {
        return leaveRequestRepository.findAll().stream()
                .filter(lr -> lr.getStatus() != null && lr.getStatus().name().equals(status))
                .toList();
    }

    public Page<LeaveRequest> searchLeaveRequests(String status, Long internId, Pageable pageable) {
        if (pageable == null) throw new IllegalArgumentException("Pageable is required");
        if (status != null && internId != null) {
            return leaveRequestRepository.findByStatusAndIntern_InternId(status, internId, pageable);
        } else if (status != null) {
            return leaveRequestRepository.findByStatus(status, pageable);
        } else if (internId != null) {
            return leaveRequestRepository.findByIntern_InternId(internId, pageable);
        } else {
            return leaveRequestRepository.findAll(pageable);
        }
    }

    public LeaveRequest getLeaveRequestById(Long id) {
        if (id == null) return null;
        return leaveRequestRepository.findById(id).orElse(null);
    }

    public LeaveRequest save(LeaveRequest leaveRequest) {
        return leaveRequestRepository.save(leaveRequest);
    }

}
