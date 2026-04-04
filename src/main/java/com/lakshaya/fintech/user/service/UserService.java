package com.lakshaya.fintech.user.service;

import com.lakshaya.fintech.access.AccessControlService;
import com.lakshaya.fintech.common.exception.InvalidInputException;
import com.lakshaya.fintech.common.exception.InvalidOperationException;
import com.lakshaya.fintech.common.exception.ResourceNotFoundException;
import com.lakshaya.fintech.user.dto.request.CreateUserRequest;
import com.lakshaya.fintech.user.dto.request.UpdateUserRequest;
import com.lakshaya.fintech.user.dto.response.UserResponse;
import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.UserStatus;
import com.lakshaya.fintech.user.mapper.UserMapper;
import com.lakshaya.fintech.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;
    private final UserMapper userMapper;

    // Create user (ADMIN only)

    /**
     * RULE 1: checkUserActive first - no exception.
     * ADMIN can create users with any role.
     * Email uniqueness checked before save.
     */
    @Transactional
    public UserResponse createUser(User admin, CreateUserRequest request) {
        // RULE 1 - always first
        accessControlService.checkUserActive(admin);

        String email = request.getEmail().trim();
        String name = request.getName().trim();

        // Check email uniqueness
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("createUser failed - email already in use: {}", email);
            throw new InvalidInputException("Email is already registered.");
        }

        User newUser = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(newUser);
        log.info("Admin userId={} created userId={} with role={}", admin.getId(), saved.getId(), saved.getRole());
        return userMapper.toResponse(saved);
    }

    // Get all users (ADMIN only)

    /**
     * RULE 1: checkUserActive first.
     * Returns paginated list - default page=10, max=50 (set in application.properties).
     * Sort by createdAt DESC should be passed in Pageable from controller.
     */
    public Page<UserResponse> getAllUsers(User admin, Pageable pageable) {
        // RULE 1 - always first
        accessControlService.checkUserActive(admin);

        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    // Update user (ADMIN only)

    /**
     * RULE 1: checkUserActive first.
     *
     * CRITICAL SELF-DEACTIVATION CHECK:
     * ADMIN cannot set their own status to INACTIVE.
     * If they did - they lock themselves out permanently.
     * -> 409 INVALID_OPERATION
     */
    @Transactional
    public UserResponse updateUser(User admin, Long targetId, UpdateUserRequest request) {
        // RULE 1 - always first
        accessControlService.checkUserActive(admin);

        // Step 2 - load target user
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> {
                    log.warn("updateUser failed - userId={} not found", targetId);
                    return new ResourceNotFoundException("User not found.");
                });

        // Step 3 - CRITICAL: self-deactivation check
        if (admin.getId().equals(targetId) && request.getStatus() == UserStatus.INACTIVE) {
            log.warn("INVALID_OPERATION: ADMIN userId={} attempted self-deactivation", admin.getId());
            throw new InvalidOperationException("Admins cannot deactivate their own account.");
        }

        // Step 4 - at least one field must be present
        if (request.getRole() == null && request.getStatus() == null) {
            throw new InvalidInputException("At least one field (role or status) must be provided.");
        }

        // Step 5 - apply only fields that are present (partial update)
        if (request.getRole() != null) {
            target.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            target.setStatus(request.getStatus());
        }

        User saved = userRepository.save(target);
        log.info("Admin userId={} updated userId={}", admin.getId(), saved.getId());
        return userMapper.toResponse(saved);
    }
}