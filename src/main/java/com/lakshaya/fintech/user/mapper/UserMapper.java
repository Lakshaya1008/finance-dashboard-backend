package com.lakshaya.fintech.user.mapper;

import com.lakshaya.fintech.user.dto.response.UserResponse;
import com.lakshaya.fintech.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Explicit mapping layer - User entity to UserResponse DTO.
 * Password is NEVER included.
 * Entity is NEVER returned directly from any service or controller.
 * Mapping happens ONLY here - never inline in services.
 */
@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.getStatus(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}

	public List<UserResponse> toResponseList(List<User> users) {
		return users.stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}
}

