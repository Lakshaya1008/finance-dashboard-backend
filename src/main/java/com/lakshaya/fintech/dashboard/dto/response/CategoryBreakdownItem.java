package com.lakshaya.fintech.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One entry per category from DB GROUP BY query.
 */
@Getter
@AllArgsConstructor
public class CategoryBreakdownItem {

    private String category;
    private BigDecimal total;
}
