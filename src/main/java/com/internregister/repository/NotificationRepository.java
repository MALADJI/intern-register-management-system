package com.internregister.repository;

import com.internregister.entity.Notification;
import com.internregister.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);

    // Optional: Auto-delete old notifications?
    // For now, let's keep them.

    long countByRecipientAndReadFalse(User recipient);
}
