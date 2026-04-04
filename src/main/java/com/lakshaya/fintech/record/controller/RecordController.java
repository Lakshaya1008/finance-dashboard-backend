package com.lakshaya.fintech.record.controller;

import com.lakshaya.fintech.common.response.ApiResponse;
import com.lakshaya.fintech.record.dto.request.CreateRecordRequest;
import com.lakshaya.fintech.record.dto.request.UpdateRecordRequest;
import com.lakshaya.fintech.record.dto.response.RecordResponse;
import com.lakshaya.fintech.record.enums.TransactionType;
import com.lakshaya.fintech.record.service.RecordService;
import com.lakshaya.fintech.security.auth.SecurityUtils;
import com.lakshaya.fintech.user.entity.User;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Record endpoints with method-level authorization.
 * Authorization annotations are coarse gates only.
 * Full access/ownership/field checks are enforced in RecordService.
 */
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    /**
     * POST /api/v1/records
     * ADMIN and ANALYST only.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<ApiResponse<RecordResponse>> createRecord(
            @Valid @RequestBody CreateRecordRequest request
    ) {
        User user = SecurityUtils.getCurrentUser();
        RecordResponse response = recordService.createRecord(user, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Record created successfully"));
    }

    /**
     * GET /api/v1/records
     * ADMIN and ANALYST only. VIEWER is blocked — dashboard endpoints serve their use case.
     * ADMIN sees all records (or filtered by userId param).
     * ANALYST sees only their own records (userId param ignored via resolveUserIdScope).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<ApiResponse<Page<RecordResponse>>> getRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        User user = SecurityUtils.getCurrentUser();
        Page<RecordResponse> response = recordService.getRecords(
                user, userId, type, category, startDate, endDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/records/{id}
     * ADMIN and ANALYST only. VIEWER is blocked — dashboard endpoints serve their use case.
     * Ownership and deleted-state checks are enforced in service.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<ApiResponse<RecordResponse>> getRecordById(
            @PathVariable Long id
    ) {
        User user = SecurityUtils.getCurrentUser();
        RecordResponse response = recordService.getRecordById(user, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PATCH /api/v1/records/{id}
     * ADMIN and ANALYST only.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<ApiResponse<RecordResponse>> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecordRequest request
    ) {
        User user = SecurityUtils.getCurrentUser();
        RecordResponse response = recordService.updateRecord(user, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Record updated successfully"));
    }

    /**
     * DELETE /api/v1/records/{id}
     * ADMIN only. Performs soft delete.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRecord(
            @PathVariable Long id
    ) {
        User user = SecurityUtils.getCurrentUser();
        recordService.deleteRecord(user, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Record deleted successfully"));
    }
}