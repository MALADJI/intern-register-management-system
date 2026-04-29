package com.internregister.security;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiting service
 * In production, consider using Redis for distributed systems
 */
@Service
public class RateLimitingService {
    
    // Store login attempts per IP address
    private final Map<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutUntil = new ConcurrentHashMap<>();
    
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 60 * 1000; // 60 seconds (1 minute)
    
    /**
     * Check if IP is locked out
     */
    public boolean isLockedOut(String ipAddress) {
        Long lockoutTime = lockoutUntil.get(ipAddress);
        if (lockoutTime != null && System.currentTimeMillis() < lockoutTime) {
            return true;
        }
        // Clear lockout if expired
        if (lockoutTime != null && System.currentTimeMillis() >= lockoutTime) {
            lockoutUntil.remove(ipAddress);
            loginAttempts.remove(ipAddress);
        }
        return false;
    }
    
    /**
     * Record a failed login attempt
     */
    public void recordFailedAttempt(String ipAddress) {
        loginAttempts.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
        
        int attempts = loginAttempts.get(ipAddress).get();
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            lockoutUntil.put(ipAddress, System.currentTimeMillis() + LOCKOUT_DURATION_MS);
            System.out.println("⚠ IP " + ipAddress + " locked out due to " + attempts + " failed login attempts");
        }
    }
    
    /**
     * Clear failed attempts on successful login
     */
    public void clearAttempts(String ipAddress) {
        loginAttempts.remove(ipAddress);
        lockoutUntil.remove(ipAddress);
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    public long getRemainingLockoutSeconds(String ipAddress) {
        Long lockoutTime = lockoutUntil.get(ipAddress);
        if (lockoutTime != null && System.currentTimeMillis() < lockoutTime) {
            return (lockoutTime - System.currentTimeMillis()) / 1000;
        }
        return 0;
    }
}

