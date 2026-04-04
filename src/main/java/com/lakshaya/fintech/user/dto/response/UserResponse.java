package com.lakshaya.fintech.user.dto.response;

import com.lakshaya.fintech.user.enums.Role;
import com.lakshaya.fintech.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Password is NEVER included.
 * Entity is NEVER returned directly - always via UserMapper.
 */
@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}