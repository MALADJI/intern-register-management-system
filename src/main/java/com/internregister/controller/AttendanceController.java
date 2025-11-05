package com.internregister.controller;

import com.internregister.entity.Attendance;
import com.internregister.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<Attendance> getAllAttendance() {
        return attendanceService.getAllAttendance();
    }

    @GetMapping("/intern/{internId}")
    public List<Attendance> getAttendanceByIntern(@PathVariable Long internId) {
        return attendanceService.getAttendanceByIntern(internId);
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@RequestBody Map<String, Object> request) {
        try {
            Long internId = null;
            String location = null;
            Double latitude = null;
            Double longitude = null;
            
            // Handle both Integer and Long for internId (JSON numbers can be parsed as Integer)
            Object internIdObj = request.get("internId");
            if (internIdObj != null) {
                if (internIdObj instanceof Integer) {
                    internId = ((Integer) internIdObj).longValue();
                } else if (internIdObj instanceof Long) {
                    internId = (Long) internIdObj;
                } else if (internIdObj instanceof Number) {
                    internId = ((Number) internIdObj).longValue();
                }
            }
            
            Object locationObj = request.get("location");
            if (locationObj != null) {
                location = locationObj.toString();
            }
            
            // Get geolocation coordinates
            Object latObj = request.get("latitude");
            if (latObj != null) {
                if (latObj instanceof Number) {
                    latitude = ((Number) latObj).doubleValue();
                } else if (latObj instanceof String) {
                    try {
                        latitude = Double.parseDouble((String) latObj);
                    } catch (NumberFormatException e) {
                        // Ignore invalid latitude
                    }
                }
            }
            
            Object lngObj = request.get("longitude");
            if (lngObj != null) {
                if (lngObj instanceof Number) {
                    longitude = ((Number) lngObj).doubleValue();
                } else if (lngObj instanceof String) {
                    try {
                        longitude = Double.parseDouble((String) lngObj);
                    } catch (NumberFormatException e) {
                        // Ignore invalid longitude
                    }
                }
            }
            
            if (internId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "internId is required"));
            }
            
            Attendance attendance = attendanceService.signIn(internId, location, latitude, longitude);
            return ResponseEntity.ok(attendance);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/signout/{attendanceId}")
    public ResponseEntity<?> signOut(@PathVariable Long attendanceId, @RequestBody(required = false) Map<String, Object> request) {
        try {
            Double latitude = null;
            Double longitude = null;
            String location = null;
            
            // Get geolocation and location from request body if provided
            if (request != null) {
                Object locationObj = request.get("location");
                if (locationObj != null) {
                    location = locationObj.toString();
                }
                
                Object latObj = request.get("latitude");
                if (latObj != null) {
                    if (latObj instanceof Number) {
                        latitude = ((Number) latObj).doubleValue();
                    } else if (latObj instanceof String) {
                        try {
                            latitude = Double.parseDouble((String) latObj);
                        } catch (NumberFormatException e) {
                            // Ignore invalid latitude
                        }
                    }
                }
                
                Object lngObj = request.get("longitude");
                if (lngObj != null) {
                    if (lngObj instanceof Number) {
                        longitude = ((Number) lngObj).doubleValue();
                    } else if (lngObj instanceof String) {
                        try {
                            longitude = Double.parseDouble((String) lngObj);
                        } catch (NumberFormatException e) {
                            // Ignore invalid longitude
                        }
                    }
                }
            }
            
            Attendance attendance = attendanceService.signOut(attendanceId, location, latitude, longitude);
            return ResponseEntity.ok(attendance);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
