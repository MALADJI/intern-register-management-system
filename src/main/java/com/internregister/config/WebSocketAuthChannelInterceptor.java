package com.internregister.config;

import com.internregister.security.JwtTokenProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

/**
 * Intercepts STOMP CONNECT messages to extract the JWT token from headers
 * and set the Spring Security principal. This is required for
 * SimpMessagingTemplate.convertAndSendToUser() to route messages to the
 * correct user's queue.
 */
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract Authorization header from STOMP connect frame
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                try {
                    if (jwtTokenProvider.validateToken(token)) {
                        String username = jwtTokenProvider.getUsername(token);
                        String role = jwtTokenProvider.getRole(token);

                        // Create authentication principal
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                        // Set the user on the STOMP session
                        accessor.setUser(auth);
                        System.out.println("✅ WebSocket authenticated user: " + username);
                    } else {
                        System.err.println("❌ WebSocket JWT token validation failed");
                    }
                } catch (Exception e) {
                    System.err.println("❌ WebSocket JWT authentication error: " + e.getMessage());
                }
            } else {
                System.out.println("⚠️ WebSocket CONNECT without Authorization header - anonymous connection");
            }
        }

        return message;
    }
}
