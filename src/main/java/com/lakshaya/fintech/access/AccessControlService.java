package com.lakshaya.fintech.access;

import com.lakshaya.fintech.common.exception.AccessDeniedException;
import com.lakshaya.fintech.common.exception.InvalidInputException;
import com.lakshaya.fintech.common.exception.InvalidOperationException;
import com.lakshaya.fintech.common.exception.UserInactiveException;
import com.lakshaya.fintech.record.dto.request.UpdateRecordRequest;
import com.lakshaya.fintech.record.entity.FinancialRecord;
import com.lakshaya.fintech.user.entity.User;
import com.lakshaya.fintech.user.enums.Role;
import com.lakshaya.fintech.user.enums.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * THE ENFORCEMENT BRAIN.
 *
 * Every service method that touches a record calls these methods
 * in the locked order:
 *
 * 1. checkUserActive(user) - ALWAYS first, no exceptions
 * 2. load entity - done in the calling service
 * 3. checkRecordState(record) - update/delete only
 * 4. checkOwnership(user, record) - single-record access
 * 5. checkFieldAccess(user, request) - update only
 * 6. business validation - date checks etc., in calling service
 * 7. execute operation - in calling service
 *
 * RULE: This class has ZERO knowledge of HTTP or controllers.
 */
@Slf4j
@Service
public class AccessControlService {

    // 1. checkUserActive

    /**
     * ALWAYS called first in every service method - no role exemption.
     * Null user -> controlled 403 (defensive against JWT bugs).
     * INACTIVE user -> 403 USER_INACTIVE.
     */
    public void checkUserActive(User user) {
        if (user == null) {
            log.warn("ACCESS_DENIED: null user passed to checkUserActive");
            throw new AccessDeniedException("Unauthenticated access.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("USER_INACTIVE: userId={}", user.getId());
            throw new UserInactiveException("User account is inactive. Access denied.");
        }
    }

    // 2. checkOwnership

    /**
     * Ensures non-ADMIN can only access their own records.
     * ADMIN bypasses - can access any record.
     * Null record -> controlled 409 (defensive).
     * equals() called on user.getId() - guaranteed non-null from JWT.
     */
    public void checkOwnership(User user, FinancialRecord record) {
        if (record == null) {
            log.warn("INVALID_OPERATION: null record passed to checkOwnership for userId={}", user.getId());
            throw new InvalidOperationException("Record not found or invalid.");
        }
        if (user.getRole() == Role.ADMIN) {
            return; // ADMIN can access any record
        }
        if (!user.getId().equals(record.getUserId())) {
            log.warn("ACCESS_DENIED: userId={} attempted to access recordId={} owned by userId={}",
                    user.getId(), record.getId(), record.getUserId());
            throw new AccessDeniedException("Access denied. You can only access your own records.");
        }
    }

    // 3. checkFieldAccess

    /**
     * ANALYST-restricted fields: amount, type, date - cannot modify.
     * ANALYST-allowed fields: category, notes - can modify.
     * Uses has*() methods - checks field PRESENCE, not value.
     */
    public void checkFieldAccess(User user, UpdateRecordRequest request) {
        if (user.getRole() != Role.ANALYST) {
            return; // Only ANALYST has restricted fields
        }
        if (request.hasAmount() || request.hasType() || request.hasDate()) {
            log.warn("ACCESS_DENIED: ANALYST userId={} attempted to modify restricted fields " +
                            "(amount={}, type={}, date={})",
                    user.getId(), request.hasAmount(), request.hasType(), request.hasDate());
            throw new AccessDeniedException(
                    "Analysts cannot modify financial truth fields: amount, type, or date.");
        }
    }

    // 4. checkRecordState

    /**
     * Prevents operating on a soft-deleted record.
     * Deleted record on fetch -> 404 (repository handles via findByIdAndIsDeletedFalse).
     * Deleted record on update/delete -> 409 (this method).
     */
    public void checkRecordState(FinancialRecord record) {
        if (record.isDeleted()) {
            log.warn("INVALID_OPERATION: attempted operation on deleted recordId={}", record.getId());
            throw new InvalidOperationException(
                    "Operation not allowed. This record has already been deleted.");
        }
    }

    // 5. resolveUserIdScope

    /**
     * Determines userId scope for list and dashboard queries.
     *
     * Non-ADMIN: always returns their own userId - requestedUserId is IGNORED.
     * ADMIN: returns requestedUserId if provided (filter), or null (all users).
     * ADMIN with requestedUserId <= 0 -> 400 (prevents garbage DB queries).
     */
    public Long resolveUserIdScope(User user, Long requestedUserId) {
        if (user.getRole() != Role.ADMIN) {
            log.debug("Scope resolved: non-ADMIN userId={} scoped to own data", user.getId());
            return user.getId();
        }
        if (requestedUserId != null && requestedUserId <= 0) {
            log.warn("INVALID_INPUT: ADMIN passed invalid userId filter={}", requestedUserId);
            throw new InvalidInputException("Invalid userId filter. Must be a positive number.");
        }
        log.debug("Scope resolved: ADMIN, requestedUserId={}", requestedUserId);
        return requestedUserId;
    }
}

