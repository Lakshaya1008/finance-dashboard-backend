package com.lakshaya.fintech.user.dto.request;

import com.lakshaya.fintech.user.enums.Role;
import com.lakshaya.fintech.user.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * ADMIN-only PATCH. Both fields optional individually.
 * "At least one must be present" check is in UserService — not here.
 * No @Valid in controller because both fields are intentionally nullable.
 */
@Getter
@Setter
public class UpdateUserRequest {

    private Role role;
    private UserStatus status;
}