package com.lakshaya.fintech.common.controller;

import com.lakshaya.fintech.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check endpoint for uptime monitoring and cron job keep-alive pings.
 *
 * Fully public — no JWT required.
 * Cron job should call GET /api/v1/health every 10-14 minutes to prevent
 * Render free tier from putting the service to sleep.
 *
 * Returns 200 OK with current server timestamp so the cron job can verify
 * the response is fresh and not a cached response from a dead container.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        Map.of(
                                "status", "UP",
                                "timestamp", Instant.now().toString()
                        )
                )
        );
    }
}