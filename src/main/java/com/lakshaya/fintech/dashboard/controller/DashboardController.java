package com.lakshaya.fintech.dashboard.controller;

import com.lakshaya.fintech.common.response.ApiResponse;
import com.lakshaya.fintech.dashboard.dto.response.CategoryBreakdownItem;
import com.lakshaya.fintech.dashboard.dto.response.DashboardSummaryResponse;
import com.lakshaya.fintech.dashboard.dto.response.MonthlyTrendItem;
import com.lakshaya.fintech.dashboard.service.DashboardService;
import com.lakshaya.fintech.security.auth.SecurityUtils;
import com.lakshaya.fintech.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints for authenticated users.
 * Coarse access gate is handled by class-level authorization.
 * Scoping and business rules are enforced in DashboardService.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

	private final DashboardService dashboardService;

	/**
	 * GET /api/v1/dashboard/summary
	 * Returns totalIncome, totalExpense, netBalance.
	 */
	@GetMapping("/summary")
	public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
			@RequestParam(required = false) Long userId
	) {
		User user = SecurityUtils.getCurrentUser();
		DashboardSummaryResponse response = dashboardService.getSummary(user, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	/**
	 * GET /api/v1/dashboard/category-breakdown
	 * Returns total per category.
	 */
	@GetMapping("/category-breakdown")
	public ResponseEntity<ApiResponse<List<CategoryBreakdownItem>>> getCategoryBreakdown(
			@RequestParam(required = false) Long userId
	) {
		User user = SecurityUtils.getCurrentUser();
		List<CategoryBreakdownItem> response = dashboardService.getCategoryBreakdown(user, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	/**
	 * GET /api/v1/dashboard/monthly-trend
	 * Returns income and expense per month.
	 */
	@GetMapping("/monthly-trend")
	public ResponseEntity<ApiResponse<List<MonthlyTrendItem>>> getMonthlyTrend(
			@RequestParam(required = false) Long userId
	) {
		User user = SecurityUtils.getCurrentUser();
		List<MonthlyTrendItem> response = dashboardService.getMonthlyTrend(user, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}
}

