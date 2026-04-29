package com.internregister.controller;

import com.internregister.entity.Attendance;
import com.internregister.entity.Intern;
import com.internregister.service.AttendanceService;
import com.internregister.service.WebSocketService;
import com.internregister.repository.InternRepository;
import com.internregister.service.ActivityLogService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final InternRepository internRepository;
    private final WebSocketService webSocketService;
    private final ActivityLogService activityLogService;

    public AttendanceController(AttendanceService attendanceService,
            InternRepository internRepository,
            WebSocketService webSocketService,
            ActivityLogService activityLogService) {
        this.attendanceService = attendanceService;
        this.internRepository = internRepository;
        this.webSocketService = webSocketService;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public ResponseEntity<?> getAllAttendance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                    size, org.springframework.data.domain.Sort.by("date").descending());
            org.springframework.data.domain.Page<Attendance> attendancePage = attendanceService
                    .getAllAttendance(pageable);
            return ResponseEntity.ok(attendancePage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/intern/{internId}")
    public List<Attendance> getAttendanceByIntern(@PathVariable Long internId) {
        System.out.println("📊 Getting attendance for intern ID: " + internId);
        List<Attendance> records = attendanceService.getAttendanceByIntern(internId);
        System.out.println("✓ Found " + records.size() + " attendance record(s)");
        return records;
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@RequestBody Map<String, Object> body) {
        try {
            System.out.println("📝 Sign-in request received: " + body);

            // Extract internId from request
            Object internIdObj = body.get("internId");
            if (internIdObj == null) {
                System.err.println("❌ Sign-in failed: internId is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "internId is required"));
            }

            Long internId;
            try {
                if (internIdObj instanceof Number) {
                    internId = ((Number) internIdObj).longValue();
                } else {
                    internId = Long.parseLong(internIdObj.toString());
                }
            } catch (NumberFormatException e) {
                System.err.println("❌ Sign-in failed: Invalid internId format: " + internIdObj);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid internId format: " + internIdObj));
            }

            System.out.println("🔍 Looking up intern with ID: " + internId);

            // Find intern
            Optional<Intern> internOpt = internRepository.findById(internId);
            if (internOpt.isEmpty()) {
                System.err.println("❌ Sign-in failed: Intern not found with id: " + internId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Intern not found with id: " + internId));
            }

            Intern intern = internOpt.get();
            System.out.println("✓ Found intern: " + intern.getName() + " (" + intern.getEmail() + ")");

            // Create attendance record
            Attendance attendance = new Attendance();
            attendance.setIntern(intern);

            // Set location and coordinates
            String location = (String) body.get("location");
            if (location != null && !location.trim().isEmpty()) {
                attendance.setLocation(location);
                System.out.println("✓ Location set: " + location);
            }

            if (body.get("latitude") != null) {
                try {
                    attendance.setLatitude(Double.valueOf(body.get("latitude").toString()));
                    System.out.println("✓ Latitude set: " + attendance.getLatitude());
                } catch (Exception e) {
                    System.err.println("⚠️ Error parsing latitude: " + e.getMessage());
                }
            }

            if (body.get("longitude") != null) {
                try {
                    attendance.setLongitude(Double.valueOf(body.get("longitude").toString()));
                    System.out.println("✓ Longitude set: " + attendance.getLongitude());
                } catch (Exception e) {
                    System.err.println("⚠️ Error parsing longitude: " + e.getMessage());
                }
            }

            // Get signature from intern if available and convert byte[] to base64 string
            if (intern.getSignature() != null && intern.getSignature().length > 0) {
                try {
                    System.out.println("📝 Converting signature from byte array (length: "
                            + intern.getSignature().length + " bytes)");
                    // Convert byte array to base64 string
                    String base64Signature = java.util.Base64.getEncoder().encodeToString(intern.getSignature());
                    System.out.println(
                            "✓ Signature converted to base64 (length: " + base64Signature.length() + " chars)");

                    // Check if signature is too large (MySQL LONGTEXT can hold 4GB, but let's be
                    // reasonable)
                    // Limit to 1MB to be safe
                    int maxSignatureLength = 1_000_000;
                    if (base64Signature.length() > maxSignatureLength) {
                        System.err.println("⚠️ Signature too large (" + base64Signature.length()
                                + " chars), truncating to " + maxSignatureLength + " chars...");
                        base64Signature = base64Signature.substring(0, maxSignatureLength);
                    }

                    attendance.setSignature(base64Signature);
                    System.out.println("✓ Signature set on attendance record (" + base64Signature.length() + " chars)");
                } catch (Exception e) {
                    System.err.println("⚠️ Error converting signature: " + e.getMessage());
                    e.printStackTrace();
                    // Continue without signature if conversion fails
                    System.out.println("ℹ️ Continuing without signature due to conversion error");
                }
            } else {
                System.out.println("ℹ️ No signature found for intern (signature is null or empty)");
            }

            // Validate required fields before saving
            if (attendance.getIntern() == null) {
                System.err.println("❌ Sign-in failed: Intern relationship is null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Intern relationship not set"));
            }

            // Save attendance
            System.out.println("💾 Attempting to save attendance record...");
            System.out.println("   - Intern ID: " + attendance.getIntern().getInternId());
            System.out.println("   - Location: " + attendance.getLocation());
            System.out.println("   - Has signature: " + (attendance.getSignature() != null));

            Attendance saved = attendanceService.signIn(attendance);
            System.out.println("✅ Sign-in saved successfully for intern ID: " + internId + " (Attendance ID: "
                    + saved.getAttendanceId() + ")");

            // Broadcast real-time update
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("attendanceId", saved.getAttendanceId());
            broadcastData.put("internId", saved.getIntern() != null ? saved.getIntern().getInternId() : null);
            broadcastData.put("status", saved.getStatus() != null ? saved.getStatus().name() : null);
            broadcastData.put("signInTime", saved.getTimeIn() != null ? saved.getTimeIn().toString() : null);
            broadcastData.put("location", saved.getLocation());
            if (saved.getIntern() != null) {
                broadcastData.put("internName", saved.getIntern().getName());
            }
            webSocketService.broadcastAttendanceUpdate("SIGNED_IN", broadcastData);

            // Log sign-in
            activityLogService.log(intern.getEmail(), "SIGN_IN",
                    "Intern signed in at " + (location != null ? location : "unknown location"), null);

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("❌ Error in signIn:");
            System.err.println("   Error type: " + e.getClass().getName());
            System.err.println("   Error message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to sign in: " + e.getMessage(),
                            "errorType", e.getClass().getSimpleName()));
        }
    }

    @PutMapping("/signout/{attendanceId}")
    public Attendance signOut(@PathVariable Long attendanceId,
            @RequestBody(required = false) Map<String, Object> body) {
        String location = null;
        Double latitude = null;
        Double longitude = null;

        if (body != null) {
            if (body.get("location") != null) {
                location = body.get("location").toString();
            }
            if (body.get("latitude") != null) {
                latitude = Double.valueOf(body.get("latitude").toString());
            }
            if (body.get("longitude") != null) {
                longitude = Double.valueOf(body.get("longitude").toString());
            }
        }

        Attendance signedOut = attendanceService.signOut(attendanceId, location, latitude, longitude);

        // Broadcast real-time update
        if (signedOut != null) {
            Map<String, Object> broadcastData = new java.util.HashMap<>();
            broadcastData.put("attendanceId", signedOut.getAttendanceId());
            broadcastData.put("internId", signedOut.getIntern() != null ? signedOut.getIntern().getInternId() : null);
            broadcastData.put("status", signedOut.getStatus() != null ? signedOut.getStatus().name() : null);
            broadcastData.put("signOutTime", signedOut.getTimeOut() != null ? signedOut.getTimeOut().toString() : null);
            broadcastData.put("location", signedOut.getLocation());
            broadcastData.put("latitude", signedOut.getLatitude());
            broadcastData.put("longitude", signedOut.getLongitude());
            if (signedOut.getIntern() != null) {
                broadcastData.put("internName", signedOut.getIntern().getName());
            }
            webSocketService.broadcastAttendanceUpdate("SIGNED_OUT", broadcastData);

            // Log sign-out
            if (signedOut.getIntern() != null) {
                activityLogService.log(signedOut.getIntern().getEmail(), "SIGN_OUT",
                        "Intern signed out at " + (location != null ? location : "unknown location"), null);
            }
        }

        return signedOut;
    }
}
