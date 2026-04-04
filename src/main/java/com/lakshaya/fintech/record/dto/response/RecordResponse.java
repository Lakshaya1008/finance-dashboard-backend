package com.lakshaya.fintech.record.dto.response;

import com.lakshaya.fintech.record.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * isDeleted is NEVER included - internal state never exposed.
 * Entity is NEVER returned directly - always via RecordMapper.
 *
 * createdBy = the userId who created this record (set from JWT on creation, never changes).
 * updatedBy = the userId who last modified this record (null if never updated).
 */
@Getter
@AllArgsConstructor
public class RecordResponse {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String category;
    private LocalDate date;
    private String notes;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}