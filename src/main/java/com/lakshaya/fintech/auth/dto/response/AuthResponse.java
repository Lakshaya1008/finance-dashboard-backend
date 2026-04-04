package com.lakshaya.fintech.auth.dto.response;

import com.lakshaya.fintech.user.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Role role;
}