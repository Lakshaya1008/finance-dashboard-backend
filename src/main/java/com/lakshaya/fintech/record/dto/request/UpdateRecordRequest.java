package com.lakshaya.fintech.record.dto.request;

import com.lakshaya.fintech.record.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update request - all fields nullable.
 *
 * CRITICAL: AccessControlService.checkFieldAccess() calls hasAmount(), hasType(), hasDate()
 * to check if an ANALYST is attempting to modify restricted fields.
 * These check PRESENCE, not value. A null amount means "not being updated".
 *
 * ANALYST-restricted: amount, type, date
 * ANALYST-allowed:    category, notes
 */
@Getter
@Setter
public class UpdateRecordRequest {

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private TransactionType type;

    private String category;

    private LocalDate date;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    // Field presence checks used by AccessControlService.checkFieldAccess()
    public boolean hasAmount() {
        return amount != null;
    }

    public boolean hasType() {
        return type != null;
    }

    public boolean hasDate() {
        return date != null;
    }

    public boolean hasCategory() {
        return category != null && !category.trim().isEmpty();
    }

    public boolean hasNotes() {
        return notes != null && !notes.trim().isEmpty();
    }
}
