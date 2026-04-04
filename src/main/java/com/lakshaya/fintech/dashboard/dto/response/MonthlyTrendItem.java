package com.lakshaya.fintech.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One entry per month from DB GROUP BY month query.
 * month format: "YYYY-MM" (e.g. "2024-03").
 * income and expense never null - COALESCE in query + defensive null guard.
 */
@Getter
@AllArgsConstructor
public class MonthlyTrendItem {

	private String month;
	private BigDecimal income;
	private BigDecimal expense;
}

