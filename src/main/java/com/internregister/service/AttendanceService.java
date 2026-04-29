package com.internregister.service;

import com.internregister.entity.Attendance;
import com.internregister.entity.AttendanceStatus;
import com.internregister.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public List<Attendance> getAttendanceByIntern(Long internId) {
        System.out.println("🔍 AttendanceService: Getting attendance for intern ID: " + internId);
        List<Attendance> records = attendanceRepository.findByInternInternId(internId);
        System.out.println("✓ AttendanceService: Found " + records.size() + " record(s)");
        return records;
    }

    public Attendance signIn(Attendance attendance) {
        try {
            System.out.println("💾 AttendanceService.signIn: Setting date and time...");
            attendance.setDate(LocalDateTime.now());
            attendance.setTimeIn(LocalDateTime.now());
            attendance.setStatus(AttendanceStatus.SIGNED_IN);

            System.out.println("💾 AttendanceService.signIn: Saving to database...");
            System.out.println("   - Intern ID: "
                    + (attendance.getIntern() != null ? attendance.getIntern().getInternId() : "null"));
            System.out.println("   - Location: " + attendance.getLocation());
            System.out.println("   - Signature length: "
                    + (attendance.getSignature() != null ? attendance.getSignature().length() : 0));

            Attendance saved = attendanceRepository.save(attendance);
            System.out.println("✅ AttendanceService.signIn: Saved successfully with ID: " + saved.getAttendanceId());
            return saved;
        } catch (Exception e) {
            System.err.println("❌ AttendanceService.signIn: Error saving attendance:");
            System.err.println("   Error type: " + e.getClass().getName());
            System.err.println("   Error message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            throw e;
        }
    }

    public Attendance signOut(Long attendanceId) {
        return signOut(attendanceId, null, null, null);
    }

    public Attendance signOut(Long attendanceId, String location, Double latitude, Double longitude) {
        Attendance record = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        record.setTimeOut(LocalDateTime.now());
        record.setStatus(AttendanceStatus.SIGNED_OUT);

        if (location != null) {
            record.setLocation(location);
        }
        if (latitude != null) {
            record.setLatitude(latitude);
        }
        if (longitude != null) {
            record.setLongitude(longitude);
        }

        return attendanceRepository.save(record);
    }

    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }

    public org.springframework.data.domain.Page<Attendance> getAllAttendance(
            org.springframework.data.domain.Pageable pageable) {
        return attendanceRepository.findAll(pageable);
    }
}
