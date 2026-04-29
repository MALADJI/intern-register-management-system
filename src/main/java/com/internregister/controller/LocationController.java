package com.internregister.controller;

import com.internregister.entity.Location;
import com.internregister.repository.LocationRepository;
import com.internregister.repository.AttendanceRepository;
import com.internregister.service.WebSocketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
public class LocationController {

    private final LocationRepository locationRepository;
    private final AttendanceRepository attendanceRepository;
    private final WebSocketService webSocketService;

    public LocationController(LocationRepository locationRepository, AttendanceRepository attendanceRepository,
            WebSocketService webSocketService) {
        this.locationRepository = locationRepository;
        this.attendanceRepository = attendanceRepository;
        this.webSocketService = webSocketService;
    }

    @GetMapping
    public ResponseEntity<List<Location>> getAllLocations() {
        try {
            List<Location> locations = locationRepository.findByActiveTrue();
            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable Long id) {
        Optional<Location> locationOpt = locationRepository.findById(id);
        return locationOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createLocation(@RequestBody Map<String, Object> body) {
        try {
            Location location = new Location();
            location.setName((String) body.get("name"));

            Object latObj = body.get("latitude");
            if (latObj instanceof Number) {
                location.setLatitude(((Number) latObj).doubleValue());
            } else if (latObj != null) {
                location.setLatitude(Double.parseDouble(latObj.toString()));
            }

            Object lngObj = body.get("longitude");
            if (lngObj instanceof Number) {
                location.setLongitude(((Number) lngObj).doubleValue());
            } else if (lngObj != null) {
                location.setLongitude(Double.parseDouble(lngObj.toString()));
            }

            Object radiusObj = body.get("radius");
            if (radiusObj instanceof Number) {
                location.setRadius(((Number) radiusObj).intValue());
            } else if (radiusObj != null) {
                location.setRadius(Integer.parseInt(radiusObj.toString()));
            }

            location.setDescription((String) body.get("description"));
            location.setActive(true);

            Location saved = locationRepository.save(location);

            // Broadcast real-time update
            webSocketService.broadcastLocationUpdate("CREATED", saved);

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create location: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLocation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "Location ID is required"));
            Optional<Location> locationOpt = locationRepository.findById(id);
            if (locationOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Location not found"));
            }

            Location location = locationOpt.get();

            if (body.containsKey("name")) {
                location.setName((String) body.get("name"));
            }

            if (body.containsKey("latitude")) {
                Object latObj = body.get("latitude");
                if (latObj instanceof Number) {
                    location.setLatitude(((Number) latObj).doubleValue());
                } else if (latObj != null) {
                    location.setLatitude(Double.parseDouble(latObj.toString()));
                }
            }

            if (body.containsKey("longitude")) {
                Object lngObj = body.get("longitude");
                if (lngObj instanceof Number) {
                    location.setLongitude(((Number) lngObj).doubleValue());
                } else if (lngObj != null) {
                    location.setLongitude(Double.parseDouble(lngObj.toString()));
                }
            }

            if (body.containsKey("radius")) {
                Object radiusObj = body.get("radius");
                if (radiusObj instanceof Number) {
                    location.setRadius(((Number) radiusObj).intValue());
                } else if (radiusObj != null) {
                    location.setRadius(Integer.parseInt(radiusObj.toString()));
                }
            }

            if (body.containsKey("description")) {
                location.setDescription((String) body.get("description"));
            }

            Location updated = locationRepository.save(location);

            // Broadcast real-time update
            webSocketService.broadcastLocationUpdate("UPDATED", updated);

            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update location: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLocation(@PathVariable Long id) {
        try {
            if (id == null) return ResponseEntity.badRequest().body(Map.of("error", "Location ID is required"));
            Optional<Location> locationOpt = locationRepository.findById(id);
            if (locationOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Location not found"));
            }

            // Soft delete by setting active to false
            Location location = locationOpt.get();
            location.setActive(false);
            locationRepository.save(location);

            // Broadcast real-time update
            webSocketService.broadcastLocationUpdate("DELETED", Map.of("locationId", id));

            return ResponseEntity.ok(Map.of("message", "Location deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete location: " + e.getMessage()));
        }
    }

    /**
     * Get locations with attendance statistics for map display
     * GET /api/locations/map
     */
    @GetMapping("/map")
    public ResponseEntity<?> getLocationsForMap() {
        try {
            List<Location> locations = locationRepository.findByActiveTrue();

            // Get attendance count for each location
            List<Map<String, Object>> locationsWithStats = locations.stream().map(location -> {
                Map<String, Object> locationMap = new HashMap<>();
                locationMap.put("locationId", location.getLocationId());
                locationMap.put("name", location.getName());
                locationMap.put("latitude", location.getLatitude());
                locationMap.put("longitude", location.getLongitude());
                locationMap.put("radius", location.getRadius());
                locationMap.put("description", location.getDescription());
                locationMap.put("active", location.getActive());

                // Count attendance records for this location
                long attendanceCount = attendanceRepository.findAll().stream()
                        .filter(attendance -> attendance.getLocation() != null &&
                                attendance.getLocation().equals(location.getName()))
                        .count();

                locationMap.put("attendanceCount", attendanceCount);

                return locationMap;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(locationsWithStats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve locations for map: " + e.getMessage()));
        }
    }
}
