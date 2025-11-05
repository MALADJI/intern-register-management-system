package com.internregister.dto;

import com.internregister.entity.LeaveRequest;
import com.internregister.entity.LeaveStatus;
import com.internregister.entity.LeaveType;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class LeaveRequestResponse {
    private Long requestId;
    private LeaveType leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LeaveStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String attachmentPath;
    private Long internId;
    private String internName;
    private String internEmail;

    public static LeaveRequestResponse fromEntity(LeaveRequest leaveRequest) {
        try {
            LeaveRequestResponse response = new LeaveRequestResponse();
            
            if (leaveRequest == null) {
                return response;
            }
            
            response.setRequestId(leaveRequest.getRequestId());
            response.setLeaveType(leaveRequest.getLeaveType());
            response.setFromDate(leaveRequest.getFromDate());
            response.setToDate(leaveRequest.getToDate());
            response.setStatus(leaveRequest.getStatus());
            response.setCreatedAt(leaveRequest.getCreatedAt());
            response.setUpdatedAt(leaveRequest.getUpdatedAt());
            response.setAttachmentPath(leaveRequest.getAttachmentPath());
            
            // Safely extract intern information - wrap in try-catch to prevent any issues
            try {
                var intern = leaveRequest.getIntern();
                if (intern != null) {
                    try {
                        response.setInternId(intern.getInternId());
                    } catch (Exception e) {
                        System.err.println("⚠️ Could not get intern ID: " + e.getMessage());
                    }
                    try {
                        response.setInternName(intern.getName());
                    } catch (Exception e) {
                        System.err.println("⚠️ Could not get intern name: " + e.getMessage());
                    }
                    try {
                        response.setInternEmail(intern.getEmail());
                    } catch (Exception e) {
                        System.err.println("⚠️ Could not get intern email: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Could not access intern object: " + e.getMessage());
                // Continue without intern info - not critical
            }
            
            return response;
        } catch (Exception e) {
            System.err.println("✗ Error converting LeaveRequest to DTO: " + e.getMessage());
            e.printStackTrace();
            // Return a minimal response
            LeaveRequestResponse response = new LeaveRequestResponse();
            if (leaveRequest != null) {
                response.setRequestId(leaveRequest.getRequestId());
            }
            return response;
        }
    }
}

