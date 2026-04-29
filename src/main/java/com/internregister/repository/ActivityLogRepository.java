package com.internregister.repository;

import com.internregister.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    Page<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<ActivityLog> findByUsernameContainingIgnoreCaseOrderByTimestampDesc(String username, Pageable pageable);
    Page<ActivityLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    Page<ActivityLog> findByActionContainingIgnoreCaseOrderByTimestampDesc(String action, Pageable pageable);

    Page<ActivityLog> findByUserRoleOrderByTimestampDesc(String userRole, Pageable pageable);

    Page<ActivityLog> findByUsernameInOrderByTimestampDesc(java.util.List<String> usernames, Pageable pageable);
    Page<ActivityLog> findByUsernameInAndUserRoleNotOrderByTimestampDesc(java.util.List<String> usernames, String excludedRole, Pageable pageable);
    Page<ActivityLog> findByUsernameInAndUserRoleInOrderByTimestampDesc(java.util.List<String> usernames, java.util.List<String> userRoles, Pageable pageable);

    Page<ActivityLog> findByUserRoleInOrderByTimestampDesc(java.util.List<String> userRoles, Pageable pageable);

    Page<ActivityLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end,
            Pageable pageable);
}
