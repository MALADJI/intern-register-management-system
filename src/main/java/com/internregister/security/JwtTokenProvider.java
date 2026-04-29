package com.internregister.security;

import com.internregister.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final Key secretKey;
    private final long validityInMs = 86400000; // 1 day

    public JwtTokenProvider(@Value("${jwt.secret:}") String jwtSecret) {
        Key key;
        // Use provided secret from environment variable or application.properties
        // If not provided, use a default fixed secret (for development only)
        if (jwtSecret != null && !jwtSecret.trim().isEmpty()) {
            try {
                // If it's a base64 encoded key, decode it
                byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
                key = Keys.hmacShaKeyFor(keyBytes);
                System.out.println("✓ JWT: Using secret key from configuration");
            } catch (Exception e) {
                // If not base64, treat as a string and generate a key from it
                byte[] keyBytes = jwtSecret.getBytes();
                // Ensure key is at least 256 bits (32 bytes) for HS256
                if (keyBytes.length < 32) {
                    byte[] paddedKey = new byte[32];
                    System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
                    keyBytes = paddedKey;
                }
                key = Keys.hmacShaKeyFor(keyBytes);
                System.out.println("✓ JWT: Using secret key from configuration (string)");
            }
        } else {
            // Default fixed secret for development (DO NOT USE IN PRODUCTION)
            // In production, always set jwt.secret in environment variable or
            // application.properties
            String defaultSecret = "MyDefaultJwtSecretKeyForDevelopmentOnlyMustBeAtLeast32CharactersLong";
            key = Keys.hmacShaKeyFor(defaultSecret.getBytes());
            System.out.println(
                    "⚠️ JWT: Using default secret key (development only). Set jwt.secret in application.properties or environment variable for production!");
        }
        this.secretKey = key;
    }

    public String createToken(User user) {
        Claims claims = Jwts.claims().setSubject(user.getUsername());
        claims.put("role", user.getRole().name());
        claims.put("sessionId", user.getCurrentSessionId());

        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            System.err.println("❌ JWT Token expired: " + ex.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            System.err.println("❌ JWT Token signature invalid: " + ex.getMessage());
            System.err.println(
                    "   This usually means the secret key changed (app was restarted) or token was tampered with");
            return false;
        } catch (io.jsonwebtoken.MalformedJwtException ex) {
            System.err.println("❌ JWT Token malformed: " + ex.getMessage());
            return false;
        } catch (io.jsonwebtoken.UnsupportedJwtException ex) {
            System.err.println("❌ JWT Token unsupported: " + ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            System.err.println("❌ JWT Token is empty or null: " + ex.getMessage());
            return false;
        } catch (Exception ex) {
            System.err.println(
                    "❌ JWT Token validation error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getRole(String token) {
        return (String) Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody()
                .get("role");
    }

    public String getSessionId(String token) {
        return (String) Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody()
                .get("sessionId");
    }
}
