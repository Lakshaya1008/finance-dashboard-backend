package com.lakshaya.fintech.record.service;

import com.lakshaya.fintech.access.AccessControlService;
import com.lakshaya.fintech.common.exception.InvalidInputException;
import com.lakshaya.fintech.common.exception.ResourceNotFoundException;
import com.lakshaya.fintech.record.dto.request.CreateRecordRequest;
import com.lakshaya.fintech.record.dto.request.UpdateRecordRequest;
import com.lakshaya.fintech.record.dto.response.RecordResponse;
import com.lakshaya.fintech.record.entity.FinancialRecord;
import com.lakshaya.fintech.record.enums.TransactionType;
import com.lakshaya.fintech.record.mapper.RecordMapper;
import com.lakshaya.fintech.record.repository.RecordRepository;
import com.lakshaya.fintech.user.entity.User;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final RecordRepository recordRepository;
    private final AccessControlService accessControlService;
    private final RecordMapper recordMapper;

    // Create record

    /**
     * ADMIN and ANALYST only (VIEWER blocked at controller).
     *
     * RULE 2: userId is ALWAYS set from JWT (user.getId()) - never from request body.
     * ADMIN creates records for themselves only. No cross-user assignment.
     */
    @Transactional
    public RecordResponse createRecord(User user, CreateRecordRequest request) {
        // Step 1 - always first
        accessControlService.checkUserActive(user);

        // Step 2 - business validation: date cannot be in the future
        if (request.getDate().isAfter(LocalDate.now())) {
            log.warn("INVALID_INPUT: userId={} submitted future date={}", user.getId(), request.getDate());
            throw new InvalidInputException("Date cannot be in the future.");
        }

        // Step 3 - userId must come from JWT only
        FinancialRecord record = FinancialRecord.builder()
                .amount(request.getAmount())
                .type(request.getType())
                .category(request.getCategory() != null ? request.getCategory().trim() : null)
                .date(request.getDate())
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .userId(user.getId())
                .isDeleted(false)
                .build();

        FinancialRecord saved = recordRepository.save(record);
        log.info("Record created: recordId={}, userId={}", saved.getId(), saved.getUserId());
        return recordMapper.toResponse(saved);
    }

    // Get records (list with filters)

    /**
     * ADMIN and ANALYST only (VIEWER blocked at controller).
     * ADMIN sees all records or filtered by userId param.
     * ANALYST always scoped to own records via resolveUserIdScope.
     */
    public Page<RecordResponse> getRecords(
            User user,
            Long filterUserId,
            TransactionType type,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        // Step 1 - always first
        accessControlService.checkUserActive(user);

        // Step 2 - scope userId based on role
        Long scopedUserId = accessControlService.resolveUserIdScope(user, filterUserId);

        // Step 3 - repository query applies isDeleted=false rule
        return recordRepository
                .findAllByFilters(scopedUserId, type, category, startDate, endDate, pageable)
                .map(recordMapper::toResponse);
    }

    // Get single record

    /**
     * ADMIN and ANALYST only (VIEWER blocked at controller).
     * ADMIN can access any record. ANALYST can only access own records.
     */
    public RecordResponse getRecordById(User user, Long recordId) {
        // Step 1 - always first
        accessControlService.checkUserActive(user);

        // Step 2 - deleted records return not-found via repository rule
        FinancialRecord record = recordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> {
                    log.warn("NOT_FOUND: recordId={} not found or deleted", recordId);
                    return new ResourceNotFoundException("Record not found.");
                });

        // Step 3 - ownership check
        accessControlService.checkOwnership(user, record);

        return recordMapper.toResponse(record);
    }

    // Update record

    /**
     * ADMIN and ANALYST only (VIEWER blocked at controller).
     */
    @Transactional
    public RecordResponse updateRecord(User user, Long recordId, UpdateRecordRequest request) {
        // Step 1 - always first
        accessControlService.checkUserActive(user);

        // Step 2 - load record even if deleted; step 3 decides response type
        FinancialRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> {
                    log.warn("NOT_FOUND: recordId={} not found for update", recordId);
                    return new ResourceNotFoundException("Record not found.");
                });

        // Step 3 - rejects already-deleted records
        accessControlService.checkRecordState(record);

        // Step 4 - non-admin ownership rule
        accessControlService.checkOwnership(user, record);

        // Step 5 - analyst restricted-field rule
        accessControlService.checkFieldAccess(user, request);

        // Step 6 - date validation only when date is being updated
        if (request.hasDate() && request.getDate().isAfter(LocalDate.now())) {
            log.warn("INVALID_INPUT: userId={} submitted future date={} on update", user.getId(), request.getDate());
            throw new InvalidInputException("Date cannot be in the future.");
        }

        // Step 7 - apply partial updates only after all checks pass
        if (request.hasAmount()) {
            record.setAmount(request.getAmount());
        }
        if (request.hasType()) {
            record.setType(request.getType());
        }
        if (request.hasCategory()) {
            record.setCategory(request.getCategory().trim());
        }
        if (request.hasDate()) {
            record.setDate(request.getDate());
        }
        if (request.hasNotes()) {
            record.setNotes(
                    request.getNotes() != null ? request.getNotes().trim() : null
            );
        }

        record.setUpdatedBy(user.getId());

        FinancialRecord saved = recordRepository.save(record);
        log.info("Record updated: recordId={}, updatedBy userId={}", saved.getId(), user.getId());
        return recordMapper.toResponse(saved);
    }

    // Delete record (soft delete)

    /**
     * ADMIN only (VIEWER and ANALYST blocked at controller).
     */
    @Transactional
    public void deleteRecord(User user, Long recordId) {
        // Step 1 - always first
        accessControlService.checkUserActive(user);

        // Step 2 - load record
        FinancialRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> {
                    log.warn("NOT_FOUND: recordId={} not found for delete", recordId);
                    return new ResourceNotFoundException("Record not found.");
                });

        // Step 3 - reject already-deleted records
        accessControlService.checkRecordState(record);

        // Step 4 - soft delete
        record.setDeleted(true);
        record.setUpdatedBy(user.getId());
        recordRepository.save(record);

        log.info("Record soft-deleted: recordId={}, deletedBy userId={}", recordId, user.getId());
    }
}