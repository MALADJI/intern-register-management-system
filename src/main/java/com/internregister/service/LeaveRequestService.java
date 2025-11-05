package com.internregister.service;

import com.internregister.entity.LeaveRequest;
import com.internregister.entity.LeaveStatus;
import com.internregister.entity.LeaveType;
import com.internregister.entity.Intern;
import com.internregister.repository.LeaveRequestRepository;
import com.internregister.repository.InternRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final InternRepository internRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, InternRepository internRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.internRepository = internRepository;
    }

    public List<LeaveRequest> getLeaveRequestsByIntern(Long internId) {
        return leaveRequestRepository.findByIntern_InternId(internId);
    }

    public LeaveRequest submitLeaveRequest(LeaveRequest leaveRequest) {
        leaveRequest.setStatus(LeaveStatus.PENDING);
        return leaveRequestRepository.save(leaveRequest);
    }
    
    public LeaveRequest submitLeaveRequest(Long internId, String fromDateStr, String toDateStr, String leaveTypeStr) {
        // Find the intern
        Intern intern = internRepository.findById(internId)
                .orElseThrow(() -> new RuntimeException("Intern not found with id: " + internId));
        
        // Parse dates
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(fromDateStr, formatter);
            toDate = LocalDate.parse(toDateStr, formatter);
        } catch (Exception e) {
            throw new RuntimeException("Invalid date format. Expected format: yyyy-MM-dd", e);
        }
        
        // Validate dates
        if (toDate.isBefore(fromDate)) {
            throw new RuntimeException("toDate cannot be before fromDate");
        }
        
        // Parse leave type
        LeaveType leaveType;
        try {
            leaveType = LeaveType.valueOf(leaveTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid leaveType. Must be one of: ANNUAL, SICK, CASUAL, EMERGENCY, OTHER, UNPAID, STUDY", e);
        }
        
        // Create leave request
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setIntern(intern);
        leaveRequest.setFromDate(fromDate);
        leaveRequest.setToDate(toDate);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStatus(LeaveStatus.PENDING);
        
        return leaveRequestRepository.save(leaveRequest);
    }

    @org.springframework.transaction.annotation.Transactional
    public LeaveRequest approveLeave(Long requestId) {
        try {
            System.out.println("  [Service] Approving leave request ID: " + requestId);
            
            if (requestId == null) {
                throw new IllegalArgumentException("Request ID cannot be null");
            }
            
            LeaveRequest request = leaveRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        System.err.println("  [Service] Leave request not found with ID: " + requestId);
                        return new RuntimeException("Leave request not found with id: " + requestId);
                    });
            
            System.out.println("  [Service] Found leave request:");
            System.out.println("    ID: " + request.getRequestId());
            System.out.println("    Current Status: " + request.getStatus());
            
            // Safely check intern
            try {
                System.out.println("    Intern: " + (request.getIntern() != null ? "ID=" + request.getIntern().getInternId() : "null"));
            } catch (Exception e) {
                System.err.println("    Warning: Could not access intern: " + e.getMessage());
            }
            
            request.setStatus(LeaveStatus.APPROVED);
            System.out.println("  [Service] Status updated to: " + request.getStatus());
            
            LeaveRequest saved = leaveRequestRepository.save(request);
            System.out.println("  [Service] Leave request saved successfully");
            System.out.println("    Saved ID: " + saved.getRequestId());
            System.out.println("    Saved Status: " + saved.getStatus());
            
            // Ensure the entity is properly loaded before returning
            saved = leaveRequestRepository.findById(saved.getRequestId())
                    .orElse(saved);
            
            return saved;
        } catch (IllegalArgumentException e) {
            System.err.println("  [Service] IllegalArgumentException approving leave request:");
            System.err.println("    Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (RuntimeException e) {
            System.err.println("  [Service] RuntimeException approving leave request:");
            System.err.println("    Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.err.println("  [Service] FATAL ERROR approving leave request:");
            System.err.println("    Message: " + e.getMessage());
            System.err.println("    Class: " + e.getClass().getName());
            e.printStackTrace();
            throw new RuntimeException("Failed to approve leave request: " + e.getMessage(), e);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public LeaveRequest rejectLeave(Long requestId) {
        try {
            System.out.println("  [Service] Rejecting leave request ID: " + requestId);
            
            LeaveRequest request = leaveRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        System.err.println("  [Service] Leave request not found with ID: " + requestId);
                        return new RuntimeException("Leave request not found with id: " + requestId);
                    });
            
            System.out.println("  [Service] Found leave request:");
            System.out.println("    ID: " + request.getRequestId());
            System.out.println("    Current Status: " + request.getStatus());
            System.out.println("    Intern: " + (request.getIntern() != null ? "ID=" + request.getIntern().getInternId() : "null"));
            
            request.setStatus(LeaveStatus.REJECTED);
            System.out.println("  [Service] Status updated to: " + request.getStatus());
            
            LeaveRequest saved = leaveRequestRepository.save(request);
            System.out.println("  [Service] Leave request saved successfully");
            System.out.println("    Saved ID: " + saved.getRequestId());
            System.out.println("    Saved Status: " + saved.getStatus());
            
            return saved;
        } catch (RuntimeException e) {
            System.err.println("  [Service] RuntimeException rejecting leave request:");
            System.err.println("    Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.err.println("  [Service] FATAL ERROR rejecting leave request:");
            System.err.println("    Message: " + e.getMessage());
            System.err.println("    Class: " + e.getClass().getName());
            e.printStackTrace();
            throw new RuntimeException("Failed to reject leave request: " + e.getMessage(), e);
        }
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        try {
            System.out.println("  [Service] Calling findAll() on repository...");
            long count = leaveRequestRepository.count();
            System.out.println("  [Service] Total leave requests in database: " + count);
            
            List<LeaveRequest> requests = leaveRequestRepository.findAll();
            System.out.println("  [Service] Repository returned " + requests.size() + " leave request(s)");
            
            // Log details of each request
            for (int i = 0; i < requests.size(); i++) {
                LeaveRequest req = requests.get(i);
                System.out.println("  [Service] Request #" + (i+1) + ":");
                System.out.println("    ID: " + req.getRequestId());
                System.out.println("    Type: " + req.getLeaveType());
                System.out.println("    Status: " + req.getStatus());
                System.out.println("    From: " + req.getFromDate());
                System.out.println("    To: " + req.getToDate());
                System.out.println("    Intern: " + (req.getIntern() != null ? "ID=" + req.getIntern().getInternId() : "null"));
            }
            
            return requests;
        } catch (Exception e) {
            System.err.println("✗ [Service] Error getting all leave requests:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  Class: " + e.getClass().getName());
            e.printStackTrace();
            // Return empty list instead of throwing exception
            System.err.println("  [Service] Returning empty list due to error");
            return new java.util.ArrayList<>();
        }
    }
    
    public List<LeaveRequest> getLeaveRequestsByStatus(String status) {
        try {
            System.out.println("  [Service] Filtering leave requests by status: " + status);
            
            // Convert string status to enum
            LeaveStatus statusEnum;
            try {
                statusEnum = LeaveStatus.valueOf(status.toUpperCase());
                System.out.println("  [Service] Converted status string '" + status + "' to enum: " + statusEnum);
            } catch (IllegalArgumentException e) {
                System.err.println("  [Service] Invalid status: " + status);
                System.err.println("  [Service] Valid statuses are: PENDING, APPROVED, REJECTED, CANCELLED");
                throw new RuntimeException("Invalid status: " + status + ". Valid statuses are: PENDING, APPROVED, REJECTED, CANCELLED", e);
            }
            
            long totalCount = leaveRequestRepository.count();
            System.out.println("  [Service] Total leave requests in database: " + totalCount);
            
            // Use repository method with enum for better performance
            List<LeaveRequest> allRequests = leaveRequestRepository.findAll();
            System.out.println("  [Service] Retrieved " + allRequests.size() + " leave request(s) from repository");
            
            // Filter by status enum
            List<LeaveRequest> filtered = allRequests.stream()
                    .filter(lr -> {
                        if (lr.getStatus() == null) {
                            System.out.println("  [Service] Warning: Leave request " + lr.getRequestId() + " has null status");
                            return false;
                        }
                        boolean matches = lr.getStatus().equals(statusEnum);
                        if (matches) {
                            System.out.println("  [Service] Found matching request ID: " + lr.getRequestId() + ", Status: " + lr.getStatus());
                        }
                        return matches;
                    })
                    .toList();
            
            System.out.println("  [Service] Filtered to " + filtered.size() + " leave request(s) with status: " + status);
            
            // Log details of each filtered request
            for (int i = 0; i < filtered.size(); i++) {
                LeaveRequest req = filtered.get(i);
                System.out.println("  [Service] Filtered Request #" + (i+1) + ":");
                System.out.println("    ID: " + req.getRequestId());
                System.out.println("    Type: " + req.getLeaveType());
                System.out.println("    Status: " + req.getStatus());
                System.out.println("    From: " + req.getFromDate());
                System.out.println("    To: " + req.getToDate());
                System.out.println("    Intern: " + (req.getIntern() != null ? "ID=" + req.getIntern().getInternId() : "null"));
            }
            
            return filtered;
        } catch (RuntimeException e) {
            System.err.println("✗ [Service] RuntimeException filtering leave requests by status:");
            System.err.println("  Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.err.println("✗ [Service] Error filtering leave requests by status:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  Class: " + e.getClass().getName());
            e.printStackTrace();
            // Return empty list instead of throwing exception
            System.err.println("  [Service] Returning empty list due to error");
            return new java.util.ArrayList<>();
        }
    }

    public Page<LeaveRequest> searchLeaveRequests(String status, Long internId, Pageable pageable) {
        try {
            System.out.println("  [Service] Searching leave requests:");
            System.out.println("    Status: " + (status != null ? status : "none"));
            System.out.println("    Intern ID: " + (internId != null ? internId : "none"));
            System.out.println("    Page: " + pageable.getPageNumber() + ", Size: " + pageable.getPageSize());
            
            Page<LeaveRequest> result = null;
            
            // Convert string status to enum if provided
            LeaveStatus statusEnum = null;
            if (status != null && !status.isEmpty()) {
                try {
                    statusEnum = LeaveStatus.valueOf(status.toUpperCase());
                    System.out.println("  [Service] Converted status string '" + status + "' to enum: " + statusEnum);
                } catch (IllegalArgumentException e) {
                    System.err.println("  [Service] Invalid status: " + status);
                    System.err.println("  [Service] Valid statuses are: PENDING, APPROVED, REJECTED, CANCELLED");
                    throw new RuntimeException("Invalid status: " + status + ". Valid statuses are: PENDING, APPROVED, REJECTED, CANCELLED", e);
                }
            }
            
            if (statusEnum != null && internId != null) {
                System.out.println("  [Service] Searching by status AND intern ID");
                result = leaveRequestRepository.findByStatusAndIntern_InternId(statusEnum, internId, pageable);
            } else if (statusEnum != null) {
                System.out.println("  [Service] Searching by status only");
                result = leaveRequestRepository.findByStatus(statusEnum, pageable);
            } else if (internId != null) {
                System.out.println("  [Service] Searching by intern ID only");
                result = leaveRequestRepository.findByIntern_InternId(internId, pageable);
            } else {
                System.out.println("  [Service] Getting all leave requests (paginated)");
                result = leaveRequestRepository.findAll(pageable);
            }
            
            System.out.println("  [Service] Search result:");
            System.out.println("    Total elements: " + result.getTotalElements());
            System.out.println("    Total pages: " + result.getTotalPages());
            System.out.println("    Current page: " + (result.getNumber() + 1));
            System.out.println("    Content size: " + result.getContent().size());
            
            return result;
        } catch (RuntimeException e) {
            System.err.println("✗ [Service] RuntimeException searching leave requests:");
            System.err.println("  Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.err.println("✗ [Service] Error searching leave requests:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  Class: " + e.getClass().getName());
            e.printStackTrace();
            throw new RuntimeException("Failed to search leave requests: " + e.getMessage(), e);
        }
    }

    public LeaveRequest getLeaveRequestById(Long id) {
        return leaveRequestRepository.findById(id).orElse(null);
    }
    public LeaveRequest save(LeaveRequest leaveRequest) {
        return leaveRequestRepository.save(leaveRequest);
    }
}
