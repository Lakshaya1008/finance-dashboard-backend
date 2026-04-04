package com.lakshaya.fintech.dashboard.service;

import com.lakshaya.fintech.access.AccessControlService;
import com.lakshaya.fintech.dashboard.dto.response.CategoryBreakdownItem;
import com.lakshaya.fintech.dashboard.dto.response.DashboardSummaryResponse;
import com.lakshaya.fintech.dashboard.dto.response.MonthlyTrendItem;
import com.lakshaya.fintech.record.enums.TransactionType;
import com.lakshaya.fintech.record.repository.RecordRepository;
import com.lakshaya.fintech.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

	private final RecordRepository recordRepository;
	private final AccessControlService accessControlService;

	// Summary

	public DashboardSummaryResponse getSummary(User user, Long filterUserId) {
		accessControlService.checkUserActive(user);
		Long scopedUserId = accessControlService.resolveUserIdScope(user, filterUserId);

		BigDecimal totalIncome = recordRepository.findTotalByType(scopedUserId, TransactionType.INCOME);
		BigDecimal totalExpense = recordRepository.findTotalByType(scopedUserId, TransactionType.EXPENSE);

		if (totalIncome == null) {
			totalIncome = BigDecimal.ZERO;
		}
		if (totalExpense == null) {
			totalExpense = BigDecimal.ZERO;
		}

		BigDecimal netBalance = totalIncome.subtract(totalExpense);
		log.debug("Dashboard summary: userId={}, income={}, expense={}, net={}",
				scopedUserId, totalIncome, totalExpense, netBalance);

		return new DashboardSummaryResponse(totalIncome, totalExpense, netBalance);
	}

	// Category breakdown

	public List<CategoryBreakdownItem> getCategoryBreakdown(User user, Long filterUserId) {
		accessControlService.checkUserActive(user);
		Long scopedUserId = accessControlService.resolveUserIdScope(user, filterUserId);

		return recordRepository.findCategoryBreakdown(scopedUserId)
				.stream()
				.map(row -> new CategoryBreakdownItem(
						(String) row[0],
						toBigDecimal(row[1])
				))
				.collect(Collectors.toList());
	}

	// Monthly trend

	public List<MonthlyTrendItem> getMonthlyTrend(User user, Long filterUserId) {
		accessControlService.checkUserActive(user);
		Long scopedUserId = accessControlService.resolveUserIdScope(user, filterUserId);

		return recordRepository.findMonthlyTrend(scopedUserId)
				.stream()
				.map(row -> new MonthlyTrendItem(
						(String) row[0],
						toBigDecimal(row[1]),
						toBigDecimal(row[2])
				))
				.collect(Collectors.toList());
	}

	private BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal bigDecimal) {
			return bigDecimal;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		return BigDecimal.ZERO;
	}
}

