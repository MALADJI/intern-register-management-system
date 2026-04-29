package com.internregister.util;

import com.internregister.entity.User;
import com.internregister.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityUtil {
    
    private final UserService userService;
    
    public SecurityUtil(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Get current authenticated user
     */
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            System.out.println("🔍 [SecurityUtil] No authentication found");
            return Optional.empty();
        }
        
        String username = authentication.getName();
        System.out.println("🔍 [SecurityUtil] Looking up user with authentication name: " + username);
        
        // Try finding by username first
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("✓ [SecurityUtil] Found user by username: " + user.getUsername() + " (Role: " + user.getRole() + ", Active: " + user.getActive() + ")");
            return userOpt;
        }
        System.out.println("⚠️ [SecurityUtil] User not found by username: " + username);
        
        // If not found by username, try finding by email (username might be an email)
        userOpt = userService.findByEmail(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("✓ [SecurityUtil] Found user by email: " + user.getEmail() + " (Role: " + user.getRole() + ", Active: " + user.getActive() + ")");
            return userOpt;
        }
        System.out.println("❌ [SecurityUtil] User not found by email either: " + username);
        
        return Optional.empty();
    }
    
    /**
     * Check if current user has SUPER_ADMIN role
     * Checks both JWT token authorities and database user role
     */
    public boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }
        
        // First check JWT token authorities (set by JwtAuthenticationFilter)
        boolean hasSuperAdminAuthority = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> "ROLE_SUPER_ADMIN".equals(auth));
        
        if (hasSuperAdminAuthority) {
            return true;
        }
        
        // Fallback: check database user role
        return getCurrentUser()
            .map(user -> user.getRole() == User.Role.SUPER_ADMIN)
            .orElse(false);
    }
    
    /**
     * Check if current user has ADMIN or SUPER_ADMIN role
     */
    public boolean isAdminOrSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }
        
        // First check JWT token authorities
        boolean hasAdminAuthority = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> "ROLE_SUPER_ADMIN".equals(auth) || "ROLE_ADMIN".equals(auth));
        
        if (hasAdminAuthority) {
            return true;
        }
        
        // Fallback: check database user role
        return getCurrentUser()
            .map(user -> {
                User.Role role = user.getRole();
                return role == User.Role.SUPER_ADMIN || role == User.Role.ADMIN;
            })
            .orElse(false);
    }
    
    /**
     * Require SUPER_ADMIN role, throw exception if not
     */
    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentRole = "unknown";
            if (authentication != null && authentication.isAuthenticated()) {
                currentRole = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("no authority");
                Optional<User> user = getCurrentUser();
                if (user.isPresent()) {
                    currentRole = user.get().getRole().name();
                }
            }
            throw new SecurityException("Access denied. SUPER_ADMIN role required. Current role: " + currentRole);
        }
    }
}

