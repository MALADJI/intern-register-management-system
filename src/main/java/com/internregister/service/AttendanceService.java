package com.internregister.service;

import com.internregister.entity.Attendance;
import com.internregister.entity.AttendanceStatus;
import com.internregister.entity.Intern;
import com.internregister.repository.AttendanceRepository;
import com.internregister.repository.InternRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final InternRepository internRepository;

    public AttendanceService(AttendanceRepository attendanceRepository, InternRepository internRepository) {
        this.attendanceRepository = attendanceRepository;
        this.internRepository = internRepository;
    }

    public List<Attendance> getAttendanceByIntern(Long internId) {
        return attendanceRepository.findByInternInternId(internId);
    }

    public Attendance signIn(Long internId, String location, Double latitude, Double longitude) {
        // Find the intern
        Intern intern = internRepository.findById(internId)
                .orElseThrow(() -> new RuntimeException("Intern not found with id: " + internId));
        
        // Create new attendance record
        Attendance attendance = new Attendance();
        attendance.setIntern(intern);
        attendance.setDate(LocalDateTime.now());
        attendance.setTimeIn(LocalDateTime.now());
        attendance.setStatus(AttendanceStatus.SIGNED_IN);
        attendance.setLocation(location);
        attendance.setLatitude(latitude);
        attendance.setLongitude(longitude);
        
        System.out.println("✓ Attendance sign-in recorded:");
        System.out.println("  Intern: " + intern.getName() + " (" + internId + ")");
        System.out.println("  Location: " + location);
        if (latitude != null && longitude != null) {
            System.out.println("  Coordinates: " + latitude + ", " + longitude);
        }
        
        return attendanceRepository.save(attendance);
    }

    public Attendance signOut(Long attendanceId, String location, Double latitude, Double longitude) {
        Attendance record = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found with id: " + attendanceId));
        
        record.setTimeOut(LocalDateTime.now());
        record.setStatus(AttendanceStatus.SIGNED_OUT);
        
        // Update location and geolocation if provided
        if (location != null) {
            record.setLocation(location);
        }
        if (latitude != null) {
            record.setLatitude(latitude);
        }
        if (longitude != null) {
            record.setLongitude(longitude);
        }
        
        System.out.println("✓ Attendance sign-out recorded:");
        System.out.println("  Attendance ID: " + record.getAttendanceId());
        if (record.getIntern() != null) {
            System.out.println("  Intern: " + record.getIntern().getName());
        }
        if (location != null) {
            System.out.println("  Location: " + location);
        }
        if (latitude != null && longitude != null) {
            System.out.println("  Coordinates: " + latitude + ", " + longitude);
        }
        
        return attendanceRepository.save(record);
    }

    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }
}
