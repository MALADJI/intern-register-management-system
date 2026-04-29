package com.internregister.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.internregister.config.SecurityHeadersConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
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
                        .requestMatchers(HttpMethod.GET, "/api/departments", "/api/system-settings/help-widget",
                                "/api/policies", "/api/policies/**")
                        .permitAll() // Allow public access
                        .requestMatchers("/api/auth/**").permitAll() // Allow authentication endpoints
                        .requestMatchers("/ws/**", "/ws").permitAll() // Allow WebSocket connections
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userService),
                        BasicAuthenticationFilter.class);

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
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, java.io.IOException {
        // Skip JWT validation for OPTIONS (preflight) requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if this is a public endpoint (don't log for public endpoints)
        String requestPath = request.getRequestURI();
        String requestMethod = request.getMethod();
        boolean isPublicEndpoint = requestPath.startsWith("/api/auth/") ||
                requestPath.startsWith("/swagger-ui/") ||
                requestPath.startsWith("/v3/api-docs/") ||
                (requestPath.equals("/api/departments") && "GET".equalsIgnoreCase(requestMethod)) ||
                (requestPath.equals("/api/system-settings/help-widget") && "GET".equalsIgnoreCase(requestMethod)) ||
                (requestPath.startsWith("/api/policies") && "GET".equalsIgnoreCase(requestMethod));

        try {
            String header = request.getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();

                System.out.println("🔍 JWT Filter: Processing request to " + requestPath);
                System.out.println("   Token length: " + token.length());
                System.out
                        .println("   Token preview: " + (token.length() > 50 ? token.substring(0, 50) + "..." : token));

                // Validate token
                boolean isValid = jwtTokenProvider.validateToken(token);
                System.out.println("   Token validation result: " + isValid);

                if (isValid) {
                    try {
                        String username = jwtTokenProvider.getUsername(token);
                        String role = jwtTokenProvider.getRole(token);
                        String sessionId = jwtTokenProvider.getSessionId(token);

                        System.out.println("   Token username: " + username);
                        System.out.println("   Token role: " + role);
                        System.out.println("   Token sessionId: " + sessionId);

                        if (username != null && role != null) {
                            User user = userService.findByUsername(username).orElse(null);
                            if (user != null) {
                                // Check if user is active
                                boolean isActive = user.getActive() == null || user.getActive();

                                // Check if session is valid (for single session control)
                                boolean isSessionValid = sessionId != null
                                        && sessionId.equals(user.getCurrentSessionId());

                                if (isActive && isSessionValid) {
                                    // Set user role as authority
                                    // Use username as authentication name (matches what's in token)
                                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                            user.getUsername(), null, java.util.List.of(authority));
                                    SecurityContextHolder.getContext().setAuthentication(auth);
                                    // Log successful authentication for debugging
                                    if (!isPublicEndpoint) {
                                        System.out.println("✓ JWT Filter: Authenticated user: " + username + " (ID: "
                                                + user.getId() + ") with role: " + role);
                                    }
                                } else if (!isActive) {
                                    // User is inactive - block access
                                    if (!isPublicEndpoint) {
                                        System.out.println(
                                                "❌ JWT Filter: User is inactive: " + username + " - Access denied");
                                    }
                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    response.setContentType("application/json");
                                    response.setCharacterEncoding("UTF-8");
                                    response.getWriter().write(
                                            "{\"error\":\"Account is inactive\",\"message\":\"Your account has been deactivated. Please contact an administrator.\"}");
                                    return;
                                } else {
                                    // Session is invalid - another login occurred elsewhere
                                    if (!isPublicEndpoint) {
                                        System.out.println("❌ JWT Filter: Session invalid for " + username + " (Token: "
                                                + sessionId + ", DB: " + user.getCurrentSessionId() + ")");
                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                        response.setContentType("application/json");
                                        response.setCharacterEncoding("UTF-8");
                                        response.getWriter().write(
                                                "{\"error\":\"Session invalidated\",\"message\":\"You have been logged out because someone else logged in with your account on another browser.\"}");
                                        return;
                                    } else {
                                        System.out.println("⚠️ JWT Filter: Session invalid for " + username
                                                + " but allowed since it is a public endpoint");
                                    }
                                }
                            } else {
                                // User not found - only log for protected endpoints
                                if (!isPublicEndpoint) {
                                    System.out.println("❌ JWT Filter: User not found by username: " + username
                                            + " (token username: " + username + ", role: " + role + ")");
                                    System.out.println("   Attempting to find by email instead...");
                                    // Try finding by email as fallback
                                    user = userService.findByEmail(username).orElse(null);
                                    if (user != null) {
                                        System.out.println("   ✓ Found user by email: " + user.getEmail()
                                                + " (username: " + user.getUsername() + ")");
                                        boolean isActive = user.getActive() == null || user.getActive();

                                        // Check if session is valid (for single session control)
                                        boolean isSessionValid = sessionId != null
                                                && sessionId.equals(user.getCurrentSessionId());

                                        if (isActive && isSessionValid) {
                                            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                                                    "ROLE_" + role);
                                            // Use the email (from token) as the authentication name so SecurityUtil can
                                            // find it
                                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                                    user.getEmail(), null, java.util.List.of(authority));
                                            SecurityContextHolder.getContext().setAuthentication(auth);
                                            System.out.println("✓ JWT Filter: Authenticated user by email: "
                                                    + user.getEmail() + " with role: " + role);
                                        } else if (!isActive) {
                                            // User is inactive - block access
                                            System.out.println("   ❌ User found but is inactive: " + user.getEmail()
                                                    + " - Access denied");
                                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                            response.setContentType("application/json");
                                            response.setCharacterEncoding("UTF-8");
                                            response.getWriter().write(
                                                    "{\"error\":\"Account is inactive\",\"message\":\"Your account has been deactivated. Please contact an administrator.\"}");
                                            return;
                                        } else {
                                            // Session is invalid - another login occurred elsewhere
                                            if (!isPublicEndpoint) {
                                                System.out.println("   ❌ Session invalid for " + user.getEmail()
                                                        + " (Token: " + sessionId + ", DB: "
                                                        + user.getCurrentSessionId() + ")");
                                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                response.setContentType("application/json");
                                                response.setCharacterEncoding("UTF-8");
                                                response.getWriter().write(
                                                        "{\"error\":\"Session invalidated\",\"message\":\"You have been logged out because someone else logged in with your account on another browser.\"}");
                                                return;
                                            } else {
                                                System.out.println("   ⚠️ Session invalid for " + user.getEmail()
                                                        + " but allowed since it is a public endpoint");
                                            }
                                        }
                                    } else {
                                        System.out.println("   ❌ User not found by email either: " + username);
                                    }
                                }
                            }
                        } else {
                            System.out.println("❌ JWT Filter: Username or role is null (username: " + username
                                    + ", role: " + role + ")");
                        }
                    } catch (Exception e) {
                        // Token parsing error - always log for debugging
                        System.err.println(
                                "❌ JWT Filter: Error parsing token for " + requestPath + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    // Invalid token - always log for debugging
                    System.err.println("❌ JWT Filter: Invalid token for " + requestPath);
                    System.err.println(
                            "   This means the token failed validation. Check previous error messages for details.");
                }
            } else {
                // No Authorization header - only log for protected endpoints
                if (!isPublicEndpoint) {
                    System.err.println("❌ JWT Filter: No Authorization header for " + requestPath);
                    System.err.println("   Header value: "
                            + (header != null ? header.substring(0, Math.min(50, header.length())) + "..." : "null"));
                }
            }
        } catch (Exception e) {
            // Log error but continue - Spring Security will handle unauthorized requests
            // Only log for protected endpoints to reduce console clutter
            if (!isPublicEndpoint) {
                System.out.println("JWT Filter: Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        filterChain.doFilter(request, response);
    }
}
