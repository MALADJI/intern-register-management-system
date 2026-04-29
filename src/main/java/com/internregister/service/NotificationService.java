package com.internregister.service;

import com.internregister.entity.Notification;
import com.internregister.entity.User;
import com.internregister.entity.Supervisor;
import com.internregister.repository.NotificationRepository;
import com.internregister.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
            WebSocketService webSocketService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
    }

    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized access to notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> notifications = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public Notification createNotification(User recipient, String message, String type, String link, String relatedId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRelatedId(relatedId);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);

        // Send real-time update via WebSocket
        try {
            // Map notification to a simple DTO to avoid recursion/lazy loading issues
            java.util.Map<String, Object> promo = new java.util.HashMap<>();
            promo.put("id", saved.getId());
            promo.put("message", saved.getMessage());
            promo.put("type", saved.getType());
            promo.put("link", saved.getLink());
            promo.put("read", saved.isRead());
            promo.put("timestamp", saved.getCreatedAt().toString());

            webSocketService.sendToUser(recipient.getUsername(), "NOTIFICATION", promo);
        } catch (Exception e) {
            System.err.println("Error sending WebSocket notification: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public void notifyAdmins(String message, String link, String relatedId) {
        List<User> admins = userRepository.findByRole(User.Role.ADMIN);
        System.out.println("🔔 [NotificationService] Found " + admins.size() + " ADMINs to notify.");
        for (User admin : admins) {
            System.out.println("  -> Notifying ADMIN: " + admin.getUsername() + " (" + admin.getEmail() + ")");
            createNotification(admin, message, "WARNING", link, relatedId);
        }
    }

    @Transactional
    public void notifySuperAdmins(String message, String link, String relatedId) {
        List<User> superAdmins = userRepository.findByRole(User.Role.SUPER_ADMIN);
        System.out.println("🔔 [NotificationService] Found " + superAdmins.size() + " SUPER_ADMINs to notify.");
        for (User superAdmin : superAdmins) {
            System.out.println(
                    "  -> Notifying SUPER_ADMIN: " + superAdmin.getUsername() + " (" + superAdmin.getEmail() + ")");
            createNotification(superAdmin, message, "INFO", link, relatedId);
        }
    }

    @Transactional
    public void notifyUserByEmail(String email, String message, String type, String link, String relatedId) {
        // Try to find user by email (case-insensitive if possible, but exact match for
        // now with logging)
        java.util.Optional<User> userOpt = userRepository.findByEmail(email).stream().findFirst();

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("🔔 [NotificationService] Notifying USER: " + user.getUsername() + " (" + email + ")");
            createNotification(user, message, type, link, relatedId);
        } else {
            System.err.println(
                    "❌ [NotificationService] Employee with email '" + email + "' not found. Notification skipped.");
            // Optional: Try finding by username if email fails? Or just log error.
        }
    }

    @Transactional
    public void notifySupervisors(List<Supervisor> supervisors, String message, String link, String relatedId) {
        System.out.println("🔔 [NotificationService] Notifying " + supervisors.size() + " Supervisors.");
        for (Supervisor supervisor : supervisors) {
            String email = supervisor.getEmail();
            java.util.Optional<User> userOpt = userRepository.findByEmail(email).stream().findFirst();

            if (userOpt.isPresent()) {
                System.out.println("  -> Notifying SUPERVISOR (User found): " + email);
                createNotification(userOpt.get(), message, "WARNING", link, relatedId);
            } else {
                System.err.println("❌ [NotificationService] Supervisor User account not found for email: " + email);
            }
        }
    }
}
