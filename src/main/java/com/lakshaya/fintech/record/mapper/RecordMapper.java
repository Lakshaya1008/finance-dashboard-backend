package com.lakshaya.fintech.record.mapper;

import com.lakshaya.fintech.record.dto.response.RecordResponse;
import com.lakshaya.fintech.record.entity.FinancialRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Explicit mapping layer - FinancialRecord entity to RecordResponse DTO.
 * isDeleted is NEVER included - internal state never exposed.
 * Entity is NEVER returned directly from any service or controller.
 * Mapping happens ONLY after all checks have passed.
 */
@Component
public class RecordMapper {

    public RecordResponse toResponse(FinancialRecord record) {
        return new RecordResponse(
                record.getId(),
                record.getAmount(),
                record.getType(),
                record.getCategory(),
                record.getDate(),
                record.getNotes(),
                record.getUserId(),    // userId in entity = createdBy in response
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getUpdatedBy()
        );
    }

    public List<RecordResponse> toResponseList(List<FinancialRecord> records) {
        return records.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}