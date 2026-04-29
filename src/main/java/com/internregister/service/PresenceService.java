package com.internregister.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();
    private final WebSocketService webSocketService;

    public PresenceService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        java.security.Principal user = event.getUser();
        if (user != null && user.getName() != null) {
            String username = user.getName();
            // A user can have multiple sessions, so we can track unique usernames
            activeUsers.add(username);
            webSocketService.broadcastUserUpdate("ONLINE", username);
            System.out.println("✅ User Online Tracker: " + username + " connected.");
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        java.security.Principal user = event.getUser();
        if (user != null && user.getName() != null) {
            String username = user.getName();
            activeUsers.remove(username);
            webSocketService.broadcastUserUpdate("OFFLINE", username);
            System.out.println("❌ User Online Tracker: " + username + " disconnected.");
        }
    }

    public Set<String> getOnlineUsers() {
        return activeUsers;
    }
}
