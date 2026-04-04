package com.lakshaya.fintech.auth.service;

import com.lakshaya.fintech.access.AccessControlService;
import com.lakshaya.fintech.auth.dto.request.LoginRequest;
import com.lakshaya.fintech.auth.dto.request.RegisterRequest;
import com.lakshaya.fintech.auth.dto.response.AuthResponse;
import com.lakshaya.fintech.common.exception.InvalidInputException;
import com.lakshaya.fintech.common.exception.UserInactiveException;
import com.lakshaya.fintech.security.jwt.JwtUtil;
import com.lakshaya.fintech.user.dto.response.UserResponse;
import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.Role;
import com.lakshaya.fintech.user.enums.UserStatus;
import com.lakshaya.fintech.user.mapper.UserMapper;
import com.lakshaya.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * Key assertions:
 * - Register always assigns VIEWER role
 * - Register encodes password with BCrypt
 * - Login anti-enumeration: same exception type for both wrong email and wrong password
 * - Login blocks INACTIVE users before issuing token
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccessControlService accessControlService;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    // ── Register ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("Duplicate email → InvalidInputException")
        void duplicateEmail_throwsInvalidInput() {
            RegisterRequest request = new RegisterRequest();
            request.setName("Test");
            request.setEmail("existing@test.com");
            request.setPassword("password123");

            when(userRepository.findByEmail("existing@test.com"))
                    .thenReturn(Optional.of(new User()));

            assertThrows(InvalidInputException.class,
                    () -> authService.register(request));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("New user always gets VIEWER role — no self-role-assignment")
        void register_alwaysAssignsViewer() {
            RegisterRequest request = new RegisterRequest();
            request.setName("New User");
            request.setEmail("new@test.com");
            request.setPassword("password123");

            when(userRepository.findByEmail("new@test.com"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode("password123"))
                    .thenReturn("$2a$encoded");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> {
                        User u = inv.getArgument(0);
                        u.setId(1L);
                        return u;
                    });
            when(userMapper.toResponse(any(User.class)))
                    .thenReturn(new UserResponse(1L, "New User", "new@test.com",
                            Role.VIEWER, UserStatus.ACTIVE, null, null));

            authService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals(Role.VIEWER, captor.getValue().getRole());
        }

        @Test
        @DisplayName("Password is encoded before saving — never stored as plain text")
        void register_encodesPassword() {
            RegisterRequest request = new RegisterRequest();
            request.setName("Test");
            request.setEmail("test@example.com");
            request.setPassword("myPlainPassword");

            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(passwordEncoder.encode("myPlainPassword")).thenReturn("$2a$encoded_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            when(userMapper.toResponse(any())).thenReturn(
                    new UserResponse(1L, "Test", "test@example.com",
                            Role.VIEWER, UserStatus.ACTIVE, null, null));

            authService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertEquals("$2a$encoded_hash", captor.getValue().getPassword());
        }
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class LoginTests {

        private User activeUser;

        @BeforeEach
        void setUp() {
            activeUser = User.builder()
                    .id(1L)
                    .name("Test")
                    .email("user@test.com")
                    .password("$2a$encoded")
                    .role(Role.ANALYST)
                    .status(UserStatus.ACTIVE)
                    .build();
        }

        @Test
        @DisplayName("Valid credentials → returns token + role")
        void validCredentials_returnsToken() {
            LoginRequest request = new LoginRequest();
            request.setEmail("user@test.com");
            request.setPassword("correct");

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("correct", "$2a$encoded")).thenReturn(true);
            when(jwtUtil.generateToken(activeUser)).thenReturn("jwt.token.here");

            AuthResponse response = authService.login(request);

            assertEquals("jwt.token.here", response.getToken());
            assertEquals(Role.ANALYST, response.getRole());
        }

        @Test
        @DisplayName("Wrong email → InvalidInputException (NOT ResourceNotFoundException)")
        void wrongEmail_throwsInvalidInput() {
            LoginRequest request = new LoginRequest();
            request.setEmail("nonexistent@test.com");
            request.setPassword("anything");

            when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

            InvalidInputException ex = assertThrows(InvalidInputException.class,
                    () -> authService.login(request));
            assertEquals("Invalid email or password.", ex.getMessage());
        }

        @Test
        @DisplayName("Wrong password → InvalidInputException")
        void wrongPassword_throwsInvalidInput() {
            LoginRequest request = new LoginRequest();
            request.setEmail("user@test.com");
            request.setPassword("wrongpass");

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongpass", "$2a$encoded")).thenReturn(false);

            InvalidInputException ex = assertThrows(InvalidInputException.class,
                    () -> authService.login(request));
            assertEquals("Invalid email or password.", ex.getMessage());
        }

        @Test
        @DisplayName("CRITICAL: Wrong email and wrong password throw SAME exception type with SAME message — anti-enumeration")
        void sameExceptionForBothFailures() {
            // Wrong email
            LoginRequest emailFail = new LoginRequest();
            emailFail.setEmail("ghost@test.com");
            emailFail.setPassword("anything");
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            InvalidInputException ex1 = assertThrows(InvalidInputException.class,
                    () -> authService.login(emailFail));

            // Wrong password
            LoginRequest passFail = new LoginRequest();
            passFail.setEmail("user@test.com");
            passFail.setPassword("wrong");
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrong", "$2a$encoded")).thenReturn(false);

            InvalidInputException ex2 = assertThrows(InvalidInputException.class,
                    () -> authService.login(passFail));

            // BOTH must be same exception type AND same message
            assertEquals(ex1.getClass(), ex2.getClass());
            assertEquals(ex1.getMessage(), ex2.getMessage());
        }

        @Test
        @DisplayName("INACTIVE user → UserInactiveException before token issuance")
        void inactiveUser_throwsUserInactive() {
            LoginRequest request = new LoginRequest();
            request.setEmail("user@test.com");
            request.setPassword("correct");

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("correct", "$2a$encoded")).thenReturn(true);
            doThrow(new UserInactiveException("User account is inactive. Access denied."))
                    .when(accessControlService).checkUserActive(activeUser);

            assertThrows(UserInactiveException.class,
                    () -> authService.login(request));

            // Token must NOT be generated
            verify(jwtUtil, never()).generateToken(any());
        }
    }
}
