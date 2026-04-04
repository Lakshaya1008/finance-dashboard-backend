package com.lakshaya.fintech.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lakshaya.fintech.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Handles 401 Unauthorized responses for unauthenticated requests.
 *
 * Without this, Spring Security returns an HTML error page for 401.
 * With this, unauthenticated requests get a clean JSON ApiResponse.
 *
 * Response: { "code": "UNAUTHORIZED", "message": "Authentication required." }
 */
@Slf4j
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException
	) throws IOException {
		log.warn("Unauthorized request to: {}", request.getRequestURI());

		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		ApiResponse<Void> body = ApiResponse.error("UNAUTHORIZED", "Authentication required.");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}

