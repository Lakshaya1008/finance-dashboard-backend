package com.lakshaya.fintech.security.jwt;

import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Standalone JWT utility. No Spring Security dependency — testable in isolation.
 *
 * Token payload:
 *   sub  = userId (as String)
 *   role = Role enum name
 *   iat  = issued at
 *   exp  = expiry
 *
 * Key is built ONCE at construction — never per call.
 * StandardCharsets.UTF_8 is explicit — never platform-dependent encoding.
 */
@Slf4j
@Component
public class JwtUtil {

    private final Key signingKey;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        // UTF_8 explicit — same bytes on every OS/JVM
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    /**
     * Returns null on failure — caller must null-check.
     * Full exception passed to log.warn (not just getMessage) — no silent failures.
     */
    public Long extractUserId(String token) {
        try {
            String subject = parseClaims(token).getSubject();
            return Long.parseLong(subject);
        } catch (Exception ex) {
            log.warn("Failed to extract userId from token", ex);
            return null;
        }
    }

    /**
     * Null-safe role extraction.
     * Separate catch for IllegalArgumentException on Role.valueOf() — unknown role value.
     */
    public Role extractRole(String token) {
        try {
            String roleStr = (String) parseClaims(token).get("role");
            if (roleStr == null) {
                log.warn("Role claim missing in token");
                return null;
            }
            return Role.valueOf(roleStr);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid role value in token: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Failed to extract role from token", ex);
            return null;
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns true if token is structurally valid: signature correct + not expired.
     * Null/blank guard before parsing — prevents NullPointerException.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            log.warn("JWT validation failed: token is null or blank");
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired", ex);
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT unsupported", ex);
        } catch (MalformedJwtException ex) {
            log.warn("JWT malformed", ex);
        } catch (SecurityException ex) {
            log.warn("JWT signature invalid", ex);
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims empty", ex);
        }
        return false;
    }

    /**
     * Stronger overload — validates token AND confirms it belongs to expectedUserId.
     * A valid token for user A must not be accepted for user B.
     */
    public boolean validateToken(String token, Long expectedUserId) {
        if (token == null || token.isBlank() || expectedUserId == null) {
            return false;
        }
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(String.valueOf(expectedUserId));
        } catch (Exception ex) {
            log.warn("JWT validation failed for userId={}", expectedUserId, ex);
            return false;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}