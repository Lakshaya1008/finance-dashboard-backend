package com.lakshaya.fintech.security.jwt;

import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.UserStatus;
import com.lakshaya.fintech.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fires exactly once per request (OncePerRequestFilter).
 *
 * Flow:
 * 1. Extract Bearer token from Authorization header
 * 2. Check SecurityContext is empty - do not overwrite existing auth
 * 3. Validate token (signature + expiry)
 * 4. Extract userId from token
 * 5. Load full User entity from DB
 * 6. Check user is ACTIVE - secondary gate (primary = checkUserActive in services)
 * 7. Set SecurityContext with User as principal
 * 8. Continue filter chain
 *
 * RULE: Full User entity loaded from DB - always fresh, always current status.
 * RULE: ROLE_ prefix required for @PreAuthorize("hasRole('ADMIN')") to work.
 *
 * NOTE: Spring Security clears context after each request in stateless mode.
 * No manual clearing needed here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;
	private final UserRepository userRepository;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {

		String token = extractToken(request);

		// Only process if context is not already set - prevents unnecessary DB hits
		// and overwrites in chained filter scenarios
		if (token != null
				&& jwtUtil.validateToken(token)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {

			Long userId = jwtUtil.extractUserId(token);

			if (userId != null) {
				userRepository.findById(userId).ifPresent(user -> {
					// Secondary safety: only ACTIVE users get a SecurityContext
					// Primary gate is checkUserActive() in every service method
					if (user.getStatus() == UserStatus.ACTIVE) {
						setSecurityContext(user);
					} else {
						log.debug("Skipping SecurityContext - userId={} is INACTIVE", user.getId());
					}
				});
			}

		} else if (token == null) {
			log.debug("No JWT token for request: {}", request.getRequestURI());
		} else if (SecurityContextHolder.getContext().getAuthentication() != null) {
			log.debug("SecurityContext already set, skipping filter for: {}", request.getRequestURI());
		} else {
			log.debug("JWT missing or invalid for request: {}", request.getRequestURI());
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * Extracts raw JWT from "Authorization: Bearer <token>" header.
	 * Returns null if header is absent or malformed.
	 */
	private String extractToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}

	/**
	 * Builds UsernamePasswordAuthenticationToken with:
	 * principal = full User entity (SecurityUtils.getCurrentUser() reads this)
	 * credentials = null (JWT trusted - no password re-check)
	 * authorities = ["ROLE_ADMIN"] / ["ROLE_ANALYST"] / ["ROLE_VIEWER"]
	 *
	 * RULE: "ROLE_" prefix is mandatory - Spring Security's @PreAuthorize
	 * strips it internally when you write hasRole('ADMIN').
	 */
	private void setSecurityContext(User user) {
		List<SimpleGrantedAuthority> authorities = List.of(
				new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
		);

		UsernamePasswordAuthenticationToken authToken =
				new UsernamePasswordAuthenticationToken(user, null, authorities);

		SecurityContextHolder.getContext().setAuthentication(authToken);
		log.debug("SecurityContext set for userId={}, role={}", user.getId(), user.getRole());
	}
}

