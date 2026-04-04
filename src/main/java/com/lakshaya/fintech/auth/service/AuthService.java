package com.lakshaya.fintech.auth.service;

import com.lakshaya.fintech.access.AccessControlService;
import com.lakshaya.fintech.auth.dto.request.LoginRequest;
import com.lakshaya.fintech.auth.dto.request.RegisterRequest;
import com.lakshaya.fintech.auth.dto.response.AuthResponse;
import com.lakshaya.fintech.common.exception.InvalidInputException;
import com.lakshaya.fintech.security.jwt.JwtUtil;
import com.lakshaya.fintech.user.dto.response.UserResponse;
import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.Role;
import com.lakshaya.fintech.user.enums.UserStatus;
import com.lakshaya.fintech.user.mapper.UserMapper;
import com.lakshaya.fintech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final JwtUtil jwtUtil;
	private final PasswordEncoder passwordEncoder;
	private final AccessControlService accessControlService;
	private final UserMapper userMapper;

	// Register

	/**
	 * Public endpoint - no auth required.
	 * New users always register as VIEWER. Role cannot be self-assigned.
	 * Duplicate email -> 400 INVALID_INPUT.
	 */
	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = request.getEmail().trim();

		// Check email uniqueness before saving
		if (userRepository.findByEmail(email).isPresent()) {
			log.warn("Register failed - email already in use: {}", email);
			throw new InvalidInputException("Email is already registered.");
		}

		User user = User.builder()
				.name(request.getName())
				.email(email)
				.password(passwordEncoder.encode(request.getPassword()))
				.role(Role.VIEWER)
				.status(UserStatus.ACTIVE)
				.build();

		User saved = userRepository.save(user);
		log.info("User registered: userId={}, role=VIEWER", saved.getId());
		return userMapper.toResponse(saved);
	}

	// Login

	/**
	 * Public endpoint - no auth required.
	 *
	 * Order:
	 * 1. Find user by email -> 400 if not found
	 * 2. Verify password -> 400 if wrong
	 * 3. checkUserActive -> 403 USER_INACTIVE if inactive
	 * 4. Generate JWT -> return token + role
	 *
	 * SECURITY: Both email-not-found and wrong-password throw InvalidInputException (400)
	 * with IDENTICAL message. No status code difference = no user enumeration.
	 */
	public AuthResponse login(LoginRequest request) {
		String email = request.getEmail().trim();

		// Step 1 - find user by email
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("Login failed - email not found");
					return new InvalidInputException("Invalid email or password.");
				});

		// Step 2 - verify password
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			log.warn("Login failed - wrong password for userId={}", user.getId());
			// Same message as email-not-found to reduce account enumeration risk
			throw new InvalidInputException("Invalid email or password.");
		}

		// Step 3 - must happen before token issuance
		accessControlService.checkUserActive(user);

		// Step 4 - generate JWT
		String token = jwtUtil.generateToken(user);
		log.info("Login successful: userId={}, role={}", user.getId(), user.getRole());
		return new AuthResponse(token, user.getRole());
	}
}

