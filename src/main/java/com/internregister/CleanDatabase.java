package com.internregister;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Clean Database Script
 * Removes all testing data and keeps only SUPER_ADMIN users
 * Run this class directly to clean the database
 */
public class CleanDatabase {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/intern_register";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Ledge.98";
    
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("Database Cleanup Script");
        System.out.println("Removing all test data, keeping only SUPER_ADMIN");
        System.out.println("=========================================");
        System.out.println();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("✓ Connected to database: intern_register");
            System.out.println();
            
            // Disable foreign key checks temporarily
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                System.out.println("✓ Disabled foreign key checks");
                System.out.println();
                
                // Step 1: Delete all leave requests
                System.out.println("Step 1: Deleting all leave requests...");
                int deletedLeaveRequests = stmt.executeUpdate("DELETE FROM leave_requests");
                System.out.println("  ✓ Deleted " + deletedLeaveRequests + " leave request(s)");
                
                // Step 2: Delete all attendance records
                System.out.println("Step 2: Deleting all attendance records...");
                int deletedAttendance = stmt.executeUpdate("DELETE FROM attendance");
                System.out.println("  ✓ Deleted " + deletedAttendance + " attendance record(s)");
                
                // Step 3: Delete all locations (if they exist and are not referenced)
                System.out.println("Step 3: Deleting all locations...");
                try {
                    int deletedLocations = stmt.executeUpdate("DELETE FROM locations");
                    System.out.println("  ✓ Deleted " + deletedLocations + " location(s)");
                } catch (SQLException e) {
                    System.out.println("  - Locations table may not exist or has constraints: " + e.getMessage());
                }
                
                // Step 4: Delete all interns
                System.out.println("Step 4: Deleting all interns...");
                int deletedInterns = stmt.executeUpdate("DELETE FROM interns");
                System.out.println("  ✓ Deleted " + deletedInterns + " intern(s)");
                
                // Step 5: Delete all supervisors
                System.out.println("Step 5: Deleting all supervisors...");
                int deletedSupervisors = stmt.executeUpdate("DELETE FROM supervisors");
                System.out.println("  ✓ Deleted " + deletedSupervisors + " supervisor(s)");
                
                // Step 6: Delete all admins
                System.out.println("Step 6: Deleting all admins...");
                int deletedAdmins = stmt.executeUpdate("DELETE FROM admins");
                System.out.println("  ✓ Deleted " + deletedAdmins + " admin(s)");
                
                // Step 7: Delete all fields
                System.out.println("Step 7: Deleting all fields...");
                int deletedFields = stmt.executeUpdate("DELETE FROM fields");
                System.out.println("  ✓ Deleted " + deletedFields + " field(s)");

                // Step 8: Delete all departments
                System.out.println("Step 8: Deleting all departments...");
                int deletedDepartments = stmt.executeUpdate("DELETE FROM departments");
                System.out.println("  ✓ Deleted " + deletedDepartments + " department(s)");

                // Step 9: Delete all users except SUPER_ADMIN
                System.out.println("Step 9: Deleting all users except SUPER_ADMIN...");
                int deletedUsers = stmt.executeUpdate("DELETE FROM users WHERE role != 'SUPER_ADMIN'");
                System.out.println("  ✓ Deleted " + deletedUsers + " user(s)");
                
                // Step 10: Delete password reset tokens
                System.out.println("Step 10: Deleting password reset tokens...");
                try {
                    int deletedTokens = stmt.executeUpdate("DELETE FROM password_reset_tokens");
                    System.out.println("  ✓ Deleted " + deletedTokens + " token(s)");
                } catch (SQLException e) {
                    System.out.println("  - Password reset tokens table may not exist: " + e.getMessage());
                }
                
                // Step 11: Delete notification preferences
                System.out.println("Step 11: Deleting notification preferences...");
                try {
                    int deletedPrefs = stmt.executeUpdate("DELETE FROM notification_preferences");
                    System.out.println("  ✓ Deleted " + deletedPrefs + " preference(s)");
                } catch (SQLException e) {
                    System.out.println("  - Notification preferences table may not exist: " + e.getMessage());
                }

                // Step 12: Delete notifications
                System.out.println("Step 12: Deleting notifications...");
                try {
                    int deletedNotifs = stmt.executeUpdate("DELETE FROM notifications");
                    System.out.println("  ✓ Deleted " + deletedNotifs + " notification(s)");
                } catch (SQLException e) {
                    System.out.println("  - Notifications table may not exist: " + e.getMessage());
                }

                // Step 13: Delete activity logs
                System.out.println("Step 13: Deleting activity logs...");
                try {
                    int deletedLogs = stmt.executeUpdate("DELETE FROM activity_logs");
                    System.out.println("  ✓ Deleted " + deletedLogs + " activity log(s)");
                } catch (SQLException e) {
                    System.out.println("  - Activity logs table may not exist: " + e.getMessage());
                }

                // Step 14: Delete intern contracts
                System.out.println("Step 14: Deleting intern contracts...");
                try {
                    int deletedContracts = stmt.executeUpdate("DELETE FROM intern_contracts");
                    System.out.println("  ✓ Deleted " + deletedContracts + " contract(s)");
                } catch (SQLException e) {
                    System.out.println("  - Intern contracts table may not exist: " + e.getMessage());
                }

                // Step 15: Delete verification codes
                System.out.println("Step 15: Deleting verification codes...");
                try {
                    int deletedCodes = stmt.executeUpdate("DELETE FROM verification_codes");
                    System.out.println("  ✓ Deleted " + deletedCodes + " code(s)");
                } catch (SQLException e) {
                    System.out.println("  - Verification codes table may not exist: " + e.getMessage());
                }

                // Step 16: Reset auto-increment IDs
                System.out.println("Step 16: Resetting auto-increment IDs...");
                try {
                    stmt.execute("ALTER TABLE leave_requests AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE attendance AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE interns AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE supervisors AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE admins AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE fields AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE departments AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE notifications AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE activity_logs AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE intern_contracts AUTO_INCREMENT = 1");
                    stmt.execute("ALTER TABLE verification_codes AUTO_INCREMENT = 1");
                    // Don't reset users table auto-increment to preserve SUPER_ADMIN ID
                    System.out.println("  ✓ Reset auto-increment IDs");
                } catch (SQLException e) {
                    System.out.println("  - Some tables may not have auto-increment: " + e.getMessage());
                }
                
                // Re-enable foreign key checks
                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                System.out.println();
                System.out.println("✓ Re-enabled foreign key checks");
                System.out.println();
                
                // Step 11: Verify SUPER_ADMIN users remain
                System.out.println("Step 11: Verifying SUPER_ADMIN users...");
                try (var rs = stmt.executeQuery("SELECT COUNT(*) as count FROM users WHERE role = 'SUPER_ADMIN'")) {
                    if (rs.next()) {
                        int superAdminCount = rs.getInt("count");
                        System.out.println("  ✓ Found " + superAdminCount + " SUPER_ADMIN user(s)");
                        
                        if (superAdminCount > 0) {
                            try (var rs2 = stmt.executeQuery("SELECT id, username, email FROM users WHERE role = 'SUPER_ADMIN'")) {
                                System.out.println("  SUPER_ADMIN users:");
                                while (rs2.next()) {
                                    System.out.println("    - ID: " + rs2.getLong("id") + 
                                                      ", Username: " + rs2.getString("username") + 
                                                      ", Email: " + rs2.getString("email"));
                                }
                            }
                        } else {
                            System.out.println("  ⚠ WARNING: No SUPER_ADMIN users found!");
                        }
                    }
                }
                
                // Step 12: Show summary
                System.out.println();
                System.out.println("=========================================");
                System.out.println("✓ SUCCESS: Database cleanup completed!");
                System.out.println("=========================================");
                System.out.println();
                System.out.println("Summary:");
                System.out.println("  - All leave requests deleted");
                System.out.println("  - All attendance records deleted");
                System.out.println("  - All interns deleted");
                System.out.println("  - All supervisors deleted");
                System.out.println("  - All admins deleted");
                System.out.println("  - All fields deleted");
                System.out.println("  - All departments deleted");
                System.out.println("  - All non-SUPER_ADMIN users deleted");
                System.out.println("  - Only SUPER_ADMIN users remain");
                System.out.println();
                
            }
            
        } catch (SQLException e) {
            System.err.println("❌ ERROR: Database cleanup failed!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            System.exit(1);
        }
        
        System.out.println("Database cleanup complete!");
    }
}

