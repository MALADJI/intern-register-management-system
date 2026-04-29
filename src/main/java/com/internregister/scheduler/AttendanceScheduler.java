package com.internregister.scheduler;

import com.internregister.entity.Attendance;
import com.internregister.entity.AttendanceStatus;
import com.internregister.repository.AttendanceRepository;
import com.internregister.service.WebSocketService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler to handle automatic attendance updates
 */
@Component
public class AttendanceScheduler {

    private final AttendanceRepository attendanceRepository;
    private final WebSocketService webSocketService;
    private final com.internregister.repository.LeaveRequestRepository leaveRequestRepository;

    public AttendanceScheduler(AttendanceRepository attendanceRepository,
            WebSocketService webSocketService,
            com.internregister.repository.LeaveRequestRepository leaveRequestRepository) {
        this.attendanceRepository = attendanceRepository;
        this.webSocketService = webSocketService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /**
     * Check for interns who haven't signed out by 17:00 (5 PM)
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkMissingSignOuts() {
        LocalTime now = LocalTime.now();
        LocalTime deadline = LocalTime.of(17, 0);

        if (now.isAfter(deadline)) {
            System.out.println("⏰ AttendanceScheduler: Checking for missing sign-outs after 17:00...");

            // Find all records that are still SIGNED_IN
            List<Attendance> missingSignOuts = attendanceRepository
                    .findByStatusAndTimeOutIsNull(AttendanceStatus.SIGNED_IN);

            if (!missingSignOuts.isEmpty()) {
                System.out.println(
                        "⏰ AttendanceScheduler: Found " + missingSignOuts.size() + " record(s) with missing sign-out.");

                for (Attendance record : missingSignOuts) {
                    // Only process records from today or older
                    if (record.getTimeIn().toLocalDate().isBefore(LocalDateTime.now().toLocalDate().plusDays(1))) {
                        record.setStatus(AttendanceStatus.NOT_SIGNED_OUT);
                        attendanceRepository.save(record);

                        // Broadcast real-time update
                        Map<String, Object> broadcastData = new HashMap<>();
                        broadcastData.put("attendanceId", record.getAttendanceId());
                        broadcastData.put("internId",
                                record.getIntern() != null ? record.getIntern().getInternId() : null);
                        broadcastData.put("status", "NOT_SIGNED_OUT");
                        broadcastData.put("location", record.getLocation());
                        if (record.getIntern() != null) {
                            broadcastData.put("internName", record.getIntern().getName());
                        }

                        webSocketService.broadcastAttendanceUpdate("NOT_SIGNED_OUT", broadcastData);
                        System.out.println("📡 AttendanceScheduler: Marked intern " +
                                (record.getIntern() != null ? record.getIntern().getName() : "unknown") +
                                " as NOT_SIGNED_OUT");
                    }
                }
            }
        }
    }

    /**
     * Check for approved leaves for TODAY and mark attendance as ON_LEAVE
     * Runs every hour to ensure status is up to date
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void markDailyLeaves() {
        System.out.println("⏰ AttendanceScheduler: Checking for approved daily leaves...");
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime startOfDay = today.atStartOfDay();
        java.time.LocalDateTime endOfDay = today.atTime(java.time.LocalTime.MAX);

        List<com.internregister.entity.LeaveRequest> requests = leaveRequestRepository.findAll();

        for (com.internregister.entity.LeaveRequest req : requests) {
            // Check if status is APPROVED and today is within range
            if (req.getStatus() == com.internregister.entity.LeaveStatus.APPROVED &&
                    req.getFromDate() != null && req.getToDate() != null &&
                    !today.isBefore(req.getFromDate()) && !today.isAfter(req.getToDate())) {

                // Check if attendance exists for this intern today
                List<Attendance> attendances = attendanceRepository.findByInternAndDateBetween(
                        req.getIntern(), startOfDay, endOfDay);

                if (attendances.isEmpty()) {
                    // Create ON_LEAVE record
                    Attendance attendance = new Attendance();
                    attendance.setIntern(req.getIntern());
                    attendance.setDate(LocalDateTime.now());
                    attendance.setStatus(AttendanceStatus.ON_LEAVE);
                    attendance.setLocation("Remote/Leave");
                    Attendance saved = attendanceRepository.save(attendance);

                    System.out.println("✅ AttendanceScheduler: Auto-marked ON_LEAVE for " + req.getIntern().getName());

                    // Broadcast
                    Map<String, Object> broadcastData = new HashMap<>();
                    broadcastData.put("attendanceId", saved.getAttendanceId());
                    broadcastData.put("internId", saved.getIntern().getInternId());
                    broadcastData.put("status", "ON_LEAVE");
                    broadcastData.put("propertyName", "status");
                    broadcastData.put("newValue", "On Leave");
                    if (saved.getIntern() != null) {
                        broadcastData.put("internName", saved.getIntern().getName());
                    }
                    webSocketService.broadcastAttendanceUpdate("CREATED", broadcastData);
                } else {
                    // Optional: Update existing record if it's not already ON_LEAVE or PRESENT?
                    // For now, assume if they signed in, that takes precedence, or if they are
                    // ON_LEAVE already, do nothing.
                    Attendance record = attendances.get(0);
                    if (record.getStatus() == AttendanceStatus.ABSENT) {
                        record.setStatus(AttendanceStatus.ON_LEAVE);
                        attendanceRepository.save(record);
                        // Broadcast ...
                    }
                }
            }
        }
    }
}
