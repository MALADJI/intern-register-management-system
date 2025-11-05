package com.internregister.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseFixService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void fixLeaveTypeEnumValues() {
        System.out.println("=== Fixing LeaveType Enum Values in Database ===");
        
        try {
            // Fix "Sick Leave" to "SICK"
            int updated1 = jdbcTemplate.update(
                "UPDATE leave_requests SET leave_type = 'SICK' WHERE leave_type = 'Sick Leave' OR leave_type = 'SICK_LEAVE' OR leave_type = 'sick leave'"
            );
            if (updated1 > 0) {
                System.out.println("  ✓ Fixed " + updated1 + " record(s) with 'Sick Leave' -> 'SICK'");
            }
            
            // Fix any other variations with spaces
            int updated2 = jdbcTemplate.update(
                "UPDATE leave_requests SET leave_type = UPPER(REPLACE(leave_type, ' ', '_')) WHERE leave_type LIKE '% %'"
            );
            if (updated2 > 0) {
                System.out.println("  ✓ Fixed " + updated2 + " record(s) with spaces in leave_type");
            }
            
            // Normalize all enum values to uppercase
            jdbcTemplate.update("UPDATE leave_requests SET leave_type = UPPER(leave_type) WHERE leave_type != UPPER(leave_type)");
            
            // Check for any remaining invalid values
            var invalidValues = jdbcTemplate.queryForList(
                "SELECT DISTINCT leave_type FROM leave_requests WHERE leave_type NOT IN ('SICK', 'ANNUAL', 'CASUAL', 'EMERGENCY', 'OTHER', 'UNPAID', 'STUDY')",
                String.class
            );
            
            if (!invalidValues.isEmpty()) {
                System.out.println("  ⚠️ Found invalid leave_type values: " + invalidValues);
                // Map known variations to correct enum values
                for (String invalid : invalidValues) {
                    String fixed = mapToValidEnum(invalid);
                    if (fixed != null) {
                        int count = jdbcTemplate.update(
                            "UPDATE leave_requests SET leave_type = ? WHERE leave_type = ?",
                            fixed, invalid
                        );
                        System.out.println("  ✓ Fixed " + count + " record(s): '" + invalid + "' -> '" + fixed + "'");
                    }
                }
            }
            
            System.out.println("=== Database Fix Complete ===");
        } catch (Exception e) {
            System.err.println("✗ Error fixing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String mapToValidEnum(String value) {
        if (value == null) return null;
        
        String normalized = value.toUpperCase().trim();
        
        // Map common variations
        if (normalized.contains("SICK") || normalized.equals("SICK LEAVE") || normalized.equals("SICK_LEAVE")) {
            return "SICK";
        }
        if (normalized.equals("ANNUAL") || normalized.equals("ANNUAL LEAVE")) {
            return "ANNUAL";
        }
        if (normalized.equals("CASUAL") || normalized.equals("CASUAL LEAVE")) {
            return "CASUAL";
        }
        if (normalized.equals("EMERGENCY") || normalized.equals("EMERGENCY LEAVE")) {
            return "EMERGENCY";
        }
        if (normalized.equals("OTHER")) {
            return "OTHER";
        }
        if (normalized.equals("UNPAID") || normalized.equals("UNPAID LEAVE")) {
            return "UNPAID";
        }
        if (normalized.equals("STUDY") || normalized.equals("STUDY LEAVE")) {
            return "STUDY";
        }
        
        return null;
    }
}

