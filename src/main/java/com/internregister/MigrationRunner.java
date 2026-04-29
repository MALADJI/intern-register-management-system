package com.internregister;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration script to remove extra reason columns from leave_requests table
 * Run this class directly to execute the migration
 */
public class MigrationRunner {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/intern_register";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Ledge.98";
    
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("MySQL Database Migration Script");
        System.out.println("Removing extra reason columns");
        System.out.println("=========================================");
        System.out.println();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("✓ Connected to database: intern_register");
            System.out.println();
            
            try (Statement stmt = conn.createStatement()) {
                // Step 1: Migrate existing decline/rejection messages to reason column
                System.out.println("Step 1: Migrating existing decline/rejection messages...");
                String migrateSQL = """
                    UPDATE leave_requests 
                    SET reason = COALESCE(
                        NULLIF(rejection_reason, ''), 
                        NULLIF(rejection_message, ''), 
                        NULLIF(decline_reason, ''), 
                        reason
                    )
                    WHERE status = 'REJECTED' 
                      AND (
                        rejection_reason IS NOT NULL OR 
                        rejection_message IS NOT NULL OR 
                        decline_reason IS NOT NULL
                      )
                    """;
                
                int updatedRows = stmt.executeUpdate(migrateSQL);
                System.out.println("  ✓ Migrated " + updatedRows + " row(s)");
                System.out.println();
                
                // Step 2: Check and remove columns
                System.out.println("Step 2: Removing extra columns...");
                
                // Check and remove rejection_reason
                if (columnExists(conn, "leave_requests", "rejection_reason")) {
                    stmt.execute("ALTER TABLE leave_requests DROP COLUMN rejection_reason");
                    System.out.println("  ✓ Removed column: rejection_reason");
                } else {
                    System.out.println("  - Column rejection_reason does not exist, skipping");
                }
                
                // Check and remove rejection_message
                if (columnExists(conn, "leave_requests", "rejection_message")) {
                    stmt.execute("ALTER TABLE leave_requests DROP COLUMN rejection_message");
                    System.out.println("  ✓ Removed column: rejection_message");
                } else {
                    System.out.println("  - Column rejection_message does not exist, skipping");
                }
                
                // Check and remove decline_reason
                if (columnExists(conn, "leave_requests", "decline_reason")) {
                    stmt.execute("ALTER TABLE leave_requests DROP COLUMN decline_reason");
                    System.out.println("  ✓ Removed column: decline_reason");
                } else {
                    System.out.println("  - Column decline_reason does not exist, skipping");
                }
                
                System.out.println();
                
                // Step 3: Verify
                System.out.println("Step 3: Verifying migration...");
                String verifySQL = """
                    SELECT COLUMN_NAME 
                    FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = 'intern_register' 
                      AND TABLE_NAME = 'leave_requests'
                      AND (COLUMN_NAME LIKE '%reason%' OR COLUMN_NAME LIKE '%message%')
                    """;
                
                try (ResultSet rs = stmt.executeQuery(verifySQL)) {
                    System.out.println("  Remaining columns with 'reason' or 'message' in name:");
                    boolean foundReason = false;
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        System.out.println("    - " + colName);
                        if ("reason".equals(colName)) {
                            foundReason = true;
                        }
                    }
                    
                    if (foundReason) {
                        System.out.println();
                        System.out.println("✓ SUCCESS: Migration completed!");
                        System.out.println("  Only the 'reason' column remains.");
                    } else {
                        System.out.println();
                        System.out.println("⚠ WARNING: 'reason' column not found!");
                    }
                }
                
            }
            
        } catch (SQLException e) {
            System.err.println("❌ ERROR: Migration failed!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            System.exit(1);
        }
        
        System.out.println();
        System.out.println("Migration complete!");
    }
    
    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'intern_register'
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """;
        
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            pstmt.setString(2, columnName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }
}

