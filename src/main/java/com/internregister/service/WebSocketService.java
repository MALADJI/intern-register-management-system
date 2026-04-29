package com.internregister.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

/**
 * Service to broadcast real-time updates via WebSocket
 */
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast leave request updates to all connected clients
     */
    public void broadcastLeaveRequestUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "LEAVE_REQUEST_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/leave-requests", message);
        System.out.println("📡 Broadcasted leave request update: " + eventType);
    }

    /**
     * Broadcast admin updates to all connected clients
     */
    public void broadcastAdminUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ADMIN_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/admins", message);
        System.out.println("📡 Broadcasted admin update: " + eventType);
    }

    /**
     * Broadcast intern updates to all connected clients
     */
    public void broadcastInternUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "INTERN_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/interns", message);
        System.out.println("📡 Broadcasted intern update: " + eventType);
    }

    /**
     * Broadcast supervisor updates to all connected clients
     */
    public void broadcastSupervisorUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "SUPERVISOR_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/supervisors", message);
        System.out.println("📡 Broadcasted supervisor update: " + eventType);
    }

    /**
     * Broadcast attendance updates to all connected clients
     */
    public void broadcastAttendanceUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ATTENDANCE_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/attendance", message);
        System.out.println("📡 Broadcasted attendance update: " + eventType);
    }

    /**
     * Broadcast user updates to all connected clients
     */
    public void broadcastUserUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "USER_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/users", message);
        System.out.println("📡 Broadcasted user update: " + eventType);
    }

    /**
     * Broadcast department updates to all connected clients
     */
    public void broadcastDepartmentUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "DEPARTMENT_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/departments", message);
        System.out.println("📡 Broadcasted department update: " + eventType);
    }

    /**
     * Broadcast location updates to all connected clients
     */
    public void broadcastLocationUpdate(String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "LOCATION_" + eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/locations", message);
        System.out.println("📡 Broadcasted location update: " + eventType);
    }

    /**
     * Broadcast activity log updates to all connected clients
     * Super Admin activities are broadcast to a separate secure channel
     */
    public void broadcastActivityLog(com.internregister.entity.ActivityLog log, Long departmentId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "ACTIVITY_LOG_CREATED");
        message.put("data", log);
        message.put("departmentId", departmentId); // For departmental filtering on client side
        message.put("timestamp", System.currentTimeMillis());

        if ("SUPER_ADMIN".equals(log.getUserRole())) {
            // Secure channel for Super Admin activities
            messagingTemplate.convertAndSend("/topic/superadmin-logs", message);
            System.out.println("📡 Broadcasted SuperAdmin action to secure channel");
        } else {
            // General channel for other activities
            messagingTemplate.convertAndSend("/topic/logs", message);
            System.out.println("📡 Broadcasted activity log update: " + log.getAction());
        }
    }

    /**
     * Send update to specific user (for personal notifications)
     */
    public void sendToUser(String username, String eventType, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", eventType);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", message);
        System.out.println("📡 Sent notification to user: " + username);
    }
}
