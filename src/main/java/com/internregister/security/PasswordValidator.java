package com.internregister.security;

import org.springframework.stereotype.Component;
import com.internregister.entity.User;

/**
 * Password validation utility
 * Enforces password requirements based on user role
 */
@Component
public class PasswordValidator {
    
    /**
     * Validate password strength (default: strict requirements)
     * @param password The password to validate
     * @return Validation result with error message if invalid
     */
    public PasswordValidationResult validate(String password) {
        return validate(password, null);
    }
    
    /**
     * Validate password strength based on role
     * @param password The password to validate
     * @param role The user role (INTERN has less strict requirements)
     * @return Validation result with error message if invalid
     */
    public PasswordValidationResult validate(String password, User.Role role) {
        if (password == null || password.trim().isEmpty()) {
            return PasswordValidationResult.invalid("Password is required");
        }
        
        // For INTERN role, use less strict requirements
        if (role == User.Role.INTERN) {
            if (password.length() < 6) {
                return PasswordValidationResult.invalid("Password must be at least 6 characters long");
            }
            // For interns, just require minimum length - no special characters required
            return PasswordValidationResult.valid();
        }
        
        // For ADMIN, SUPERVISOR, and SUPER_ADMIN, use strict requirements
        if (password.length() < 8) {
            return PasswordValidationResult.invalid("Password must be at least 8 characters long");
        }
        
        if (!password.matches(".*[a-z].*")) {
            return PasswordValidationResult.invalid("Password must contain at least one lowercase letter");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            return PasswordValidationResult.invalid("Password must contain at least one uppercase letter");
        }
        
        if (!password.matches(".*\\d.*")) {
            return PasswordValidationResult.invalid("Password must contain at least one digit");
        }
        
        if (!password.matches(".*[@$!%*?&].*")) {
            return PasswordValidationResult.invalid("Password must contain at least one special character (@$!%*?&)");
        }
        
        return PasswordValidationResult.valid();
    }
    
    /**
     * Validation result class
     */
    public static class PasswordValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private PasswordValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static PasswordValidationResult valid() {
            return new PasswordValidationResult(true, null);
        }
        
        public static PasswordValidationResult invalid(String errorMessage) {
            return new PasswordValidationResult(false, errorMessage);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}

