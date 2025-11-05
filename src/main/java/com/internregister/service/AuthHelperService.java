package com.internregister.service;

import com.internregister.entity.User;
import com.internregister.entity.Intern;
import com.internregister.repository.UserRepository;
import com.internregister.repository.InternRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthHelperService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InternRepository internRepository;

    /**
     * Get the currently authenticated user
     */
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username));
    }

    /**
     * Get the current user's role
     */
    public Optional<String> getCurrentUserRole() {
        return getCurrentUser().map(user -> user.getRole().name());
    }

    /**
     * Get the current user's intern ID if they are an intern
     */
    public Optional<Long> getCurrentInternId() {
        Optional<User> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        
        User user = userOpt.get();
        if (user.getRole() != User.Role.INTERN) {
            return Optional.empty();
        }
        
        // Find intern by email
        Optional<Intern> internOpt = internRepository.findByEmail(user.getEmail());
        return internOpt.map(Intern::getInternId);
    }

    /**
     * Check if current user has a specific role
     */
    public boolean hasRole(String role) {
        return getCurrentUserRole()
                .map(r -> r.equalsIgnoreCase(role))
                .orElse(false);
    }

    /**
     * Check if current user is ADMIN
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Check if current user is SUPERVISOR
     */
    public boolean isSupervisor() {
        return hasRole("SUPERVISOR");
    }

    /**
     * Check if current user is INTERN
     */
    public boolean isIntern() {
        return hasRole("INTERN");
    }
}

