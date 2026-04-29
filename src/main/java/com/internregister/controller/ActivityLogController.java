package com.internregister.controller;

import com.internregister.entity.ActivityLog;
import com.internregister.service.ActivityLogService;
import com.internregister.util.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
public class ActivityLogController {
    private final ActivityLogService activityLogService;
    private final SecurityUtil securityUtil;

    public ActivityLogController(ActivityLogService activityLogService, SecurityUtil securityUtil) {
        this.activityLogService = activityLogService;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    public ResponseEntity<Page<ActivityLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String role) {

        // Get currently authenticated user
        java.util.Optional<com.internregister.entity.User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        com.internregister.entity.User currentUser = currentUserOpt.get();
        Pageable pageable = PageRequest.of(page, size);

        // If specific filters are provided, they take precedence BUT we still need to
        // ensure
        // they are within the user's allowed visibility.
        // For simplicity and to strictly follow the role-vibility rules requested,
        // we use the getLogsForUser which handles the role-based filtering.

        Page<ActivityLog> logs = activityLogService.getLogsForUser(currentUser, pageable);

        return ResponseEntity.ok(logs);
    }
}
