package com.internregister.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;
import com.internregister.entity.User;
import com.internregister.service.UserService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import com.internregister.config.SecurityHeadersConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    
    @Autowired(required = false)
    private SecurityHeadersConfig securityHeadersConfig;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, UserService userService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow preflight requests
                .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // TEMPORARY: Allow testing without authentication - ALL endpoints
                .requestMatchers("/api/**").permitAll()
                .anyRequest().permitAll() // Allow all requests for testing
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    System.err.println("✗ Authentication entry point triggered: " + authException.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    System.err.println("✗ Access denied: " + accessDeniedException.getMessage());
                    System.err.println("  Request URI: " + request.getRequestURI());
                    System.err.println("  User: " + (request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "null"));
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Access denied: " + accessDeniedException.getMessage() + "\"}");
                })
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    System.out.println("✗ Authentication entry point triggered:");
                    System.out.println("  Endpoint: " + request.getMethod() + " " + request.getRequestURI());
                    System.out.println("  Reason: " + authException.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    System.out.println("✗ Access denied handler triggered:");
                    System.out.println("  Endpoint: " + request.getMethod() + " " + request.getRequestURI());
                    System.out.println("  Reason: " + accessDeniedException.getMessage());
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null) {
                        System.out.println("  Authenticated user: " + auth.getName());
                        System.out.println("  Authorities: " + auth.getAuthorities());
                    }
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                })
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userService), BasicAuthenticationFilter.class);
        
        // Add security headers filter if available
        if (securityHeadersConfig != null) {
            http.addFilterAfter(securityHeadersConfig.securityHeadersFilter(), BasicAuthenticationFilter.class);
        }
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins for development
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "Content-Disposition", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserService userService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, java.io.IOException {
        // Skip JWT validation for OPTIONS (preflight) requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestPath = request.getRequestURI();
        String requestMethod = request.getMethod();
        
        // Skip authentication for public endpoints (handled by SecurityConfig, but log for debugging)
        if (requestPath.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String header = request.getHeader("Authorization");
        
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                // Trim whitespace and newlines from header
                header = header.trim();
                String token = header.substring(7).trim();
                
                // Validate token
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsername(token);
                    String role = jwtTokenProvider.getRole(token);
                    
                    System.out.println("✓ JWT Authentication successful:");
                    System.out.println("  Username: " + username);
                    System.out.println("  Role: " + role);
                    System.out.println("  Endpoint: " + requestMethod + " " + requestPath);
                    
                    // Try to find user by username first
                    User user = userService.findByUsername(username).orElse(null);
                    
                    // If not found by username, try by email
                    if (user == null) {
                        user = userService.findByEmail(username).orElse(null);
                    }
                    
                    if (user != null) {
                        // Set user role as authority
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, java.util.List.of(authority));
                        auth.setAuthenticated(true); // Explicitly mark as authenticated
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        System.out.println("  ✓ Authentication set in SecurityContext: true");
                        System.out.println("  ✓ User authenticated: " + user.getUsername() + " (Role: " + role + ")");
                    } else {
                        System.out.println("⚠ User not found in database for username: " + username);
                        System.out.println("⚠ Authentication will NOT be set - request may be rejected");
                    }
                } else {
                    System.out.println("✗ JWT Token validation failed:");
                    System.out.println("  Endpoint: " + requestMethod + " " + requestPath);
                    System.out.println("  Reason: Invalid or expired token");
                    // Clear any existing authentication
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                System.err.println("✗ JWT Authentication error:");
                System.err.println("  Endpoint: " + requestMethod + " " + requestPath);
                System.err.println("  Error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // No Authorization header - this is expected for public endpoints
            // For protected endpoints, Spring Security will handle the 401 response
            if (!requestPath.startsWith("/api/auth/") && 
                !requestPath.startsWith("/swagger-ui/") && 
                !requestPath.startsWith("/v3/api-docs/")) {
                System.out.println("⚠ No Authorization header found:");
                System.out.println("  Endpoint: " + requestMethod + " " + requestPath);
                System.out.println("  This request will be rejected if endpoint requires authentication");
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
