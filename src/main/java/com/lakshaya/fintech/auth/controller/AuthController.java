package com.lakshaya.fintech.auth.controller;

import com.lakshaya.fintech.auth.dto.request.LoginRequest;
import com.lakshaya.fintech.auth.dto.request.RegisterRequest;
import com.lakshaya.fintech.auth.dto.response.AuthResponse;
import com.lakshaya.fintech.auth.service.AuthService;
import com.lakshaya.fintech.common.response.ApiResponse;
import com.lakshaya.fintech.user.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints - no JWT required, no @PreAuthorize.
 * Zero business logic. Delegates entirely to AuthService.
 * Validation annotations trigger DTO validation and GlobalExceptionHandler formats failures.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	/**
	 * POST /api/v1/auth/register
	 * Public. Registers a new user as VIEWER.
	 */
	@PostMapping("/register")
	public ResponseEntity<ApiResponse<UserResponse>> register(
			@Valid @RequestBody RegisterRequest request
	) {
		UserResponse response = authService.register(request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "User registered successfully"));
	}

	/**
	 * POST /api/v1/auth/login
	 * Public. Returns JWT token plus role on success.
	 */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(
			@Valid @RequestBody LoginRequest request
	) {
		AuthResponse response = authService.login(request);
		return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
	}
}

