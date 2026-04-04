package com.lakshaya.fintech.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * All fields BigDecimal. Never null - return BigDecimal.ZERO on empty dataset.
 * netBalance = totalIncome - totalExpense (computed in DashboardService).
 */
@Getter
@AllArgsConstructor
public class DashboardSummaryResponse {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
}
