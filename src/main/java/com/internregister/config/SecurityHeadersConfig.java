package com.internregister.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * Security headers configuration
 * Adds security headers to all HTTP responses
 */
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public OncePerRequestFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }
    
    private static class SecurityHeadersFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                      @NonNull HttpServletResponse response, 
                                      @NonNull FilterChain filterChain) 
                throws ServletException, IOException {
            
            // Skip security headers for OPTIONS requests (CORS preflight)
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Prevent XSS attacks
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // Prevent clickjacking - Allow cross-origin for development
            // For production, consider: default-src 'self'; connect-src 'self' http://localhost:*
            // Relaxed CSP for development - allow all connections
            response.setHeader("Content-Security-Policy", "default-src 'self' 'unsafe-inline' 'unsafe-eval' http://localhost:* https://localhost:*; connect-src 'self' http://localhost:* https://localhost:* ws://localhost:* wss://localhost:*; script-src 'self' 'unsafe-inline' 'unsafe-eval' http://localhost:*; style-src 'self' 'unsafe-inline' http://localhost:*;");
            
            // Prevent MIME type sniffing
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            
            // Referrer policy
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // Ensure CORS headers are not blocked - Allow all origins for development
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "*");
            }
            
            filterChain.doFilter(request, response);
        }
    }
}

