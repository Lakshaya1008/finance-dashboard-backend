package com.lakshaya.fintech.user.controller;

import com.lakshaya.fintech.common.response.ApiResponse;
import com.lakshaya.fintech.security.auth.SecurityUtils;
import com.lakshaya.fintech.user.dto.request.CreateUserRequest;
import com.lakshaya.fintech.user.dto.request.UpdateUserRequest;
import com.lakshaya.fintech.user.dto.response.UserResponse;
import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only controller.
 * Class-level role check is only a coarse gate.
 * Real enforcement (active-user checks) runs inside UserService.
 * No business logic here.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

	private final UserService userService;

	/**
	 * POST /api/v1/users
	 * Creates a new user with any role. ADMIN only.
	 */
	@PostMapping
	public ResponseEntity<ApiResponse<UserResponse>> createUser(
			@Valid @RequestBody CreateUserRequest request
	) {
		User admin = SecurityUtils.getCurrentUser();
		UserResponse response = userService.createUser(admin, request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "User created successfully"));
	}

	/**
	 * GET /api/v1/users
	 * Returns paginated list of all users.
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
			@PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		User admin = SecurityUtils.getCurrentUser();
		Page<UserResponse> response = userService.getAllUsers(admin, pageable);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	/**
	 * PATCH /api/v1/users/{id}
	 * Updates role and/or status of target user.
	 */
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<UserResponse>> updateUser(
			@PathVariable Long id,
			@RequestBody UpdateUserRequest request
	) {
		User admin = SecurityUtils.getCurrentUser();
		UserResponse response = userService.updateUser(admin, id, request);
		return ResponseEntity.ok(ApiResponse.success(response, "User updated successfully"));
	}
}

