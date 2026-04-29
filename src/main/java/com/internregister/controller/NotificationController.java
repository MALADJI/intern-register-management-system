package com.internregister.controller;

import com.internregister.entity.Notification;
import com.internregister.entity.User;
import com.internregister.service.NotificationService;
import com.internregister.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtil securityUtil;

    public NotificationController(NotificationService notificationService, SecurityUtil securityUtil) {
        this.notificationService = notificationService;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    public ResponseEntity<?> getMyNotifications() {
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        User user = currentUserOpt.get();
        List<Notification> notifications = notificationService.getUserNotifications(user);

        // Return structured data
        // Map to simpler structure if needed, or return entity if no cycles
        // Entity has User which might cause cycles if not handled.
        // Creating simple DTO list is safer.
        List<Map<String, Object>> response = notifications.stream().map(n -> Map.of(
                "id", n.getId(),
                "message", (Object) n.getMessage(),
                "type", (Object) (n.getType() != null ? n.getType() : "INFO"),
                "read", (Object) n.isRead(),
                "timestamp", (Object) n.getCreatedAt().toString(),
                "link", (Object) (n.getLink() != null ? n.getLink() : "")))
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(currentUserOpt.get())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        try {
            notificationService.markAsRead(id, currentUserOpt.get());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        notificationService.markAllAsRead(currentUserOpt.get());
        return ResponseEntity.ok(Map.of("success", true));
    }
}
