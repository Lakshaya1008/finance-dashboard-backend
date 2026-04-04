package com.lakshaya.fintech.security.auth;

import com.lakshaya.fintech.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Central utility for extracting the authenticated User from SecurityContext.
 *
 * RULE: Every controller calls SecurityUtils.getCurrentUser() - never access
 * SecurityContextHolder directly in controllers.
 *
 * JwtFilter populates the SecurityContext with a UsernamePasswordAuthenticationToken
 * whose principal is the full User entity loaded from DB by userId in the JWT.
 *
 * Usage in any controller method:
 * User user = SecurityUtils.getCurrentUser();
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // utility class - no instances
    }

    /**
     * Returns the authenticated User from the current request's SecurityContext.
     * Throws IllegalStateException if called on an unauthenticated request.
     * This should never happen on protected routes since JwtFilter blocks first.
     */
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in SecurityContext");
        }
        return (User) auth.getPrincipal();
    }
}

