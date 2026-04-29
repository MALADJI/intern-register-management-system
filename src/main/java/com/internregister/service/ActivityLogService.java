package com.internregister.service;

import com.internregister.entity.ActivityLog;
import com.internregister.entity.User;
import com.internregister.repository.ActivityLogRepository;
import com.internregister.repository.UserRepository;
import com.internregister.repository.AdminRepository;
import com.internregister.repository.InternRepository;
import com.internregister.repository.SupervisorRepository;
import com.internregister.entity.Admin;
import com.internregister.entity.Supervisor;
import com.internregister.entity.Intern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ActivityLogService {
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final InternRepository internRepository;
    private final SupervisorRepository supervisorRepository;
    private final AdminRepository adminRepository;
    private final WebSocketService webSocketService;

    public ActivityLogService(ActivityLogRepository activityLogRepository,
            UserRepository userRepository,
            InternRepository internRepository,
            SupervisorRepository supervisorRepository,
            AdminRepository adminRepository,
            WebSocketService webSocketService) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.internRepository = internRepository;
        this.supervisorRepository = supervisorRepository;
        this.adminRepository = adminRepository;
        this.webSocketService = webSocketService;
    }

    @Transactional
    public void log(String username, String action, String details, String ipAddress) {
        String role = "UNKNOWN";
        Long userId = null;

        Optional<User> userOpt = userRepository.findByUsername(username).stream().findFirst();
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            role = user.getRole().name();
            userId = user.getId();
        }

        ActivityLog log = ActivityLog.builder()
                .timestamp(LocalDateTime.now())
                .username(username)
                .userId(userId)
                .userRole(role)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        ActivityLog savedLog = activityLogRepository.save(log);

        // Find department ID for real-time filtering on client side
        Long departmentId = null;
        if (User.Role.ADMIN.name().equals(role)) {
            departmentId = adminRepository.findByEmail(username).stream().findFirst()
                    .map(a -> a.getDepartment() != null ? a.getDepartment().getDepartmentId() : null)
                    .orElse(null);
        } else if (User.Role.SUPERVISOR.name().equals(role)) {
            departmentId = supervisorRepository.findByEmail(username).stream().findFirst()
                    .map(s -> s.getDepartment() != null ? s.getDepartment().getDepartmentId() : null)
                    .orElse(null);
        } else if (User.Role.INTERN.name().equals(role)) {
            departmentId = internRepository.findByEmail(username).stream().findFirst()
                    .map(i -> i.getDepartment() != null ? i.getDepartment().getDepartmentId() : null)
                    .orElse(null);
        }

        // Broadcast real-time update
        try {
            // We'll pass departmentId specifically to the broadcast method
            webSocketService.broadcastActivityLog(savedLog, departmentId);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast activity log: " + e.getMessage());
        }
    }

    @SuppressWarnings("null")
    public Page<ActivityLog> getAllLogs(Pageable pageable) {
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<ActivityLog> searchLogsByUsername(String username, Pageable pageable) {
        return activityLogRepository.findByUsernameContainingIgnoreCaseOrderByTimestampDesc(username, pageable);
    }

    public Page<ActivityLog> searchLogsByAction(String action, Pageable pageable) {
        return activityLogRepository.findByActionContainingIgnoreCaseOrderByTimestampDesc(action, pageable);
    }

    public Page<ActivityLog> getLogsByRole(String role, Pageable pageable) {
        return activityLogRepository.findByUserRoleOrderByTimestampDesc(role, pageable);
    }

    public Page<ActivityLog> getLogsForUser(User user, Pageable pageable) {
        User.Role role = user.getRole();

        // Super Admin sees EVERYTHING
        if (role == User.Role.SUPER_ADMIN) {
            return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
        }

        if (role == User.Role.ADMIN) {
            List<String> usernames = new ArrayList<>();
            usernames.add(user.getUsername());

            // Find Admin's department to filter logs
            adminRepository.findByEmail(user.getEmail()).stream().findFirst().ifPresent(admin -> {
                if (admin.getDepartment() != null) {
                    Long deptId = admin.getDepartment().getDepartmentId();

                    // Add all admins in this department (except self if desired, but inclusion is fine)
                    usernames.addAll(adminRepository.findByDepartmentId(deptId).stream()
                            .map(Admin::getEmail)
                            .filter(email -> email != null)
                            .collect(Collectors.toList()));

                    // Add all supervisors in this department
                    usernames.addAll(supervisorRepository.findByDepartmentId(deptId).stream()
                            .map(Supervisor::getEmail)
                            .filter(email -> email != null)
                            .collect(Collectors.toList()));

                    // Add all interns in this department
                    usernames.addAll(internRepository.findByDepartmentId(deptId).stream()
                            .map(Intern::getEmail)
                            .filter(email -> email != null)
                            .collect(Collectors.toList()));
                }
            });

            // Admins see Admin, Supervisor, and Intern logs in their department. 
            // They MUST NOT see Super Admin activities.
            List<String> allowedRoles = Arrays.asList(User.Role.ADMIN.name(), User.Role.SUPERVISOR.name(), User.Role.INTERN.name());
            return activityLogRepository.findByUsernameInAndUserRoleInOrderByTimestampDesc(usernames, allowedRoles, pageable);
        }

        if (role == User.Role.SUPERVISOR) {
            List<String> usernames = new ArrayList<>();
            usernames.add(user.getUsername());

            // Find interns explicitly assigned to this supervisor
            supervisorRepository.findByEmail(user.getEmail()).stream().findFirst().ifPresent(supervisor -> {
                if (supervisor.getInterns() != null) {
                    List<String> internEmails = supervisor.getInterns().stream()
                            .map(Intern::getEmail)
                            .filter(email -> email != null)
                            .collect(Collectors.toList());
                    usernames.addAll(internEmails);
                }
            });

            // Supervisors only see Supervisor and Intern activities for their group.
            // They MUST NOT see Admin or Super Admin activities.
            List<String> allowedRoles = Arrays.asList(User.Role.SUPERVISOR.name(), User.Role.INTERN.name());
            return activityLogRepository.findByUsernameInAndUserRoleInOrderByTimestampDesc(usernames, allowedRoles, pageable);
        }

        if (role == User.Role.INTERN) {
            // Interns only see their OWN logs. 
            return activityLogRepository.findByUsernameOrderByTimestampDesc(user.getUsername(), pageable);
        }

        // Default: If somehow an unknown role is authenticated, return only their own logs (safe fallback)
        System.out.println("⚠️ [ActivityLogService] Fallback to own logs for user: " + user.getUsername() + " (Role: " + role + ")");
        return activityLogRepository.findByUsernameOrderByTimestampDesc(user.getUsername(), pageable);
    }
}
