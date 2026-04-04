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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AccessControlService — the enforcement brain.
 *
 * No Spring context needed. No mocks needed.
 * This class has zero dependencies — pure logic testing.
 */
class AccessControlServiceTest {

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlService();
    }

    // ── Helper builders ─────────────────────────────────────────────────────

    private User buildUser(Long id, Role role, UserStatus status) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email("test@example.com")
                .password("encoded")
                .role(role)
                .status(status)
                .build();
    }

    private FinancialRecord buildRecord(Long id, Long userId) {
        return FinancialRecord.builder()
                .id(id)
                .userId(userId)
                .isDeleted(false)
                .build();
    }

    // ── checkUserActive ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkUserActive")
    class CheckUserActiveTests {

        @Test
        @DisplayName("null user → AccessDeniedException (defensive null guard)")
        void nullUser_throwsAccessDenied() {
            assertThrows(AccessDeniedException.class,
                    () -> accessControlService.checkUserActive(null));
        }

        @Test
        @DisplayName("ACTIVE user → passes silently")
        void activeUser_passes() {
            User user = buildUser(1L, Role.VIEWER, UserStatus.ACTIVE);
            assertDoesNotThrow(() -> accessControlService.checkUserActive(user));
        }

        @Test
        @DisplayName("INACTIVE user → UserInactiveException")
        void inactiveUser_throwsUserInactive() {
            User user = buildUser(1L, Role.ADMIN, UserStatus.INACTIVE);
            assertThrows(UserInactiveException.class,
                    () -> accessControlService.checkUserActive(user));
        }
    }

    // ── checkOwnership ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkOwnership")
    class CheckOwnershipTests {

        @Test
        @DisplayName("null record → InvalidOperationException (defensive null guard)")
        void nullRecord_throwsInvalidOperation() {
            User user = buildUser(1L, Role.VIEWER, UserStatus.ACTIVE);
            assertThrows(InvalidOperationException.class,
                    () -> accessControlService.checkOwnership(user, null));
        }

        @Test
        @DisplayName("ADMIN → bypasses ownership check for any record")
        void adminBypass_passes() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            FinancialRecord otherUserRecord = buildRecord(10L, 999L);
            assertDoesNotThrow(() -> accessControlService.checkOwnership(admin, otherUserRecord));
        }

        @Test
        @DisplayName("Owner accessing own record → passes")
        void ownerAccess_passes() {
            User user = buildUser(1L, Role.ANALYST, UserStatus.ACTIVE);
            FinancialRecord ownRecord = buildRecord(10L, 1L);
            assertDoesNotThrow(() -> accessControlService.checkOwnership(user, ownRecord));
        }

        @Test
        @DisplayName("Non-owner accessing other user's record → AccessDeniedException")
        void nonOwner_throwsAccessDenied() {
            User user = buildUser(1L, Role.VIEWER, UserStatus.ACTIVE);
            FinancialRecord otherRecord = buildRecord(10L, 2L);
            assertThrows(AccessDeniedException.class,
                    () -> accessControlService.checkOwnership(user, otherRecord));
        }
    }

    // ── checkFieldAccess ────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkFieldAccess")
    class CheckFieldAccessTests {

        @Test
        @DisplayName("ADMIN can modify all fields including amount, type, date")
        void adminCanModifyAll() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            UpdateRecordRequest request = new UpdateRecordRequest();
            request.setAmount(new java.math.BigDecimal("500"));
            request.setType(com.lakshaya.fintech.record.enums.TransactionType.INCOME);
            request.setDate(java.time.LocalDate.now());

            assertDoesNotThrow(() -> accessControlService.checkFieldAccess(admin, request));
        }

        @Test
        @DisplayName("ANALYST can modify category and notes (allowed fields)")
        void analystCanModifyCategoryAndNotes() {
            User analyst = buildUser(1L, Role.ANALYST, UserStatus.ACTIVE);
            UpdateRecordRequest request = new UpdateRecordRequest();
            request.setCategory("Food");
            request.setNotes("Updated notes");

            assertDoesNotThrow(() -> accessControlService.checkFieldAccess(analyst, request));
        }

        @Test
        @DisplayName("ANALYST modifying amount → AccessDeniedException")
        void analystModifyAmount_throwsAccessDenied() {
            User analyst = buildUser(1L, Role.ANALYST, UserStatus.ACTIVE);
            UpdateRecordRequest request = new UpdateRecordRequest();
            request.setAmount(new java.math.BigDecimal("999"));

            assertThrows(AccessDeniedException.class,
                    () -> accessControlService.checkFieldAccess(analyst, request));
        }

        @Test
        @DisplayName("ANALYST modifying type → AccessDeniedException")
        void analystModifyType_throwsAccessDenied() {
            User analyst = buildUser(1L, Role.ANALYST, UserStatus.ACTIVE);
            UpdateRecordRequest request = new UpdateRecordRequest();
            request.setType(com.lakshaya.fintech.record.enums.TransactionType.EXPENSE);

            assertThrows(AccessDeniedException.class,
                    () -> accessControlService.checkFieldAccess(analyst, request));
        }

        @Test
        @DisplayName("ANALYST modifying date → AccessDeniedException")
        void analystModifyDate_throwsAccessDenied() {
            User analyst = buildUser(1L, Role.ANALYST, UserStatus.ACTIVE);
            UpdateRecordRequest request = new UpdateRecordRequest();
            request.setDate(java.time.LocalDate.now());

            assertThrows(AccessDeniedException.class,
                    () -> accessControlService.checkFieldAccess(analyst, request));
        }
    }

    // ── checkRecordState ────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkRecordState")
    class CheckRecordStateTests {

        @Test
        @DisplayName("Active record → passes")
        void activeRecord_passes() {
            FinancialRecord record = buildRecord(1L, 1L);
            record.setDeleted(false);
            assertDoesNotThrow(() -> accessControlService.checkRecordState(record));
        }

        @Test
        @DisplayName("Deleted record → InvalidOperationException (409 not 404)")
        void deletedRecord_throwsInvalidOperation() {
            FinancialRecord record = buildRecord(1L, 1L);
            record.setDeleted(true);
            assertThrows(InvalidOperationException.class,
                    () -> accessControlService.checkRecordState(record));
        }
    }

    // ── resolveUserIdScope ──────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveUserIdScope")
    class ResolveUserIdScopeTests {

        @Test
        @DisplayName("Non-ADMIN always returns own userId regardless of requestedUserId")
        void nonAdmin_alwaysOwnId() {
            User viewer = buildUser(5L, Role.VIEWER, UserStatus.ACTIVE);
            Long result = accessControlService.resolveUserIdScope(viewer, 999L);
            assertEquals(5L, result);
        }

        @Test
        @DisplayName("ADMIN with null filter → returns null (all users)")
        void admin_nullFilter_returnsNull() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            Long result = accessControlService.resolveUserIdScope(admin, null);
            assertNull(result);
        }

        @Test
        @DisplayName("ADMIN with valid userId → passes through")
        void admin_validFilter_passthrough() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            Long result = accessControlService.resolveUserIdScope(admin, 42L);
            assertEquals(42L, result);
        }

        @Test
        @DisplayName("ADMIN with negative userId → InvalidInputException")
        void admin_negativeId_throws() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            assertThrows(InvalidInputException.class,
                    () -> accessControlService.resolveUserIdScope(admin, -1L));
        }

        @Test
        @DisplayName("ADMIN with zero userId → InvalidInputException")
        void admin_zeroId_throws() {
            User admin = buildUser(1L, Role.ADMIN, UserStatus.ACTIVE);
            assertThrows(InvalidInputException.class,
                    () -> accessControlService.resolveUserIdScope(admin, 0L));
        }
    }
}
