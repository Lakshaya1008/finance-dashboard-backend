package com.lakshaya.fintech.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lakshaya.fintech.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Handles 403 Forbidden responses when an authenticated user lacks the required role.
 *
 * WITHOUT this: Spring Security returns HTML 403 or falls through to AuthEntryPoint (401).
 * WITH this: role-denied requests get a clean JSON 403 ACCESS_DENIED response.
 *
 * Scenario: VIEWER hits POST /api/v1/records (ADMIN/ANALYST only)
 * Before fix: 401 "Authentication required" — wrong, misleading
 * After fix:  403 "ACCESS_DENIED" — correct, clear
 *
 * Registered in SecurityConfig via .exceptionHandling(ex -> ex.accessDeniedHandler(...))
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn("ACCESS_DENIED: {} attempted to access {}",
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "unknown",
                request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                "ACCESS_DENIED",
                "Your role does not have permission to access this endpoint."
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}