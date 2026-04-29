package com.internregister.runner;

import com.internregister.entity.User;
import com.internregister.repository.UserRepository;
import com.internregister.repository.NotificationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DatabaseDiagnosticRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public DatabaseDiagnosticRunner(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        java.io.File file = new java.io.File("diagnostic.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
            writer.println("=================================================");
            writer.println("DIAGNOSTIC: Checking Database State for Notifications");
            writer.println("=================================================");

            // 0. Check Super Admins
            List<User> superAdmins = userRepository.findByRole(User.Role.SUPER_ADMIN);
            writer.println("Found " + superAdmins.size() + " SUPER_ADMIN users.");
            for (User sa : superAdmins) {
                writer.println(" - SuperAdmin: " + sa.getUsername() + ", Email: " + sa.getEmail());
            }

            // 1. Check Admins
            List<User> admins = userRepository.findByRole(User.Role.ADMIN);
            writer.println("Found " + admins.size() + " ADMIN users.");
            for (User admin : admins) {
                writer.println(" - Admin: " + admin.getUsername() + ", Email: " + admin.getEmail() + ", Active: "
                        + admin.getActive());
            }

            // 2. Check Supervisors
            List<User> supervisors = userRepository.findByRole(User.Role.SUPERVISOR);
            writer.println("Found " + supervisors.size() + " SUPERVISOR users.");
            for (User supervisor : supervisors) {
                writer.println(" - Supervisor: " + supervisor.getUsername() + ", Email: " + supervisor.getEmail());
            }

            // 3. Check Interns
            List<User> interns = userRepository.findByRole(User.Role.INTERN);
            writer.println("Found " + interns.size() + " INTERN users.");

            // 4. Check Recent Notifications
            long notificationCount = notificationRepository.count();
            writer.println("Total Notifications in DB: " + notificationCount);

            writer.println("=================================================");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
