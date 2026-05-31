package com.smartuniversity.controller;

import com.smartuniversity.service.TokenService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthUtils authUtils;

    /**
     * Get current token usage for authenticated user
     * GET /api/tokens/usage
     */
    @GetMapping("/usage")
    public ResponseEntity<?> getCurrentTokenUsage() {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            var tokenUsage = tokenService.getTokenUsageForToday(userId);
            return ResponseEntity.ok(tokenUsage);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve token usage: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get token statistics for authenticated user
     * GET /api/tokens/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getTokenStatistics() {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            var stats = tokenService.getTokenStatistics(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve token statistics: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get token usage history for authenticated user
     * GET /api/tokens/history?days=7
     */
    @GetMapping("/history")
    public ResponseEntity<?> getTokenHistory(
            @RequestParam(defaultValue = "30") int days) {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            var history = tokenService.getTokenUsageHistory(userId, days);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve token history: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get transaction history for authenticated user
     * GET /api/tokens/transactions?limit=50
     */
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactionHistory(
            @RequestParam(defaultValue = "50") int limit) {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            var transactions = tokenService.getTransactionHistory(userId, limit);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve transaction history: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Reset tokens for a specific user (admin only)
     * POST /api/tokens/reset/{userId}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reset/{userId}")
    public ResponseEntity<?> resetUserTokens(@PathVariable Long userId) {
        try {
            tokenService.resetDailyTokens(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Tokens reset successfully for user " + userId);
            response.put("tokensRemaining", 500000L);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to reset tokens: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Check if authenticated user has enough tokens
     * GET /api/tokens/check/{requiredTokens}
     */
    @GetMapping("/check/{requiredTokens}")
    public ResponseEntity<?> checkTokenAvailability(
            @PathVariable Long requiredTokens) {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        try {
            boolean hasEnough = tokenService.hasEnoughTokens(userId, requiredTokens);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("requiredTokens", requiredTokens);
            response.put("hasEnoughTokens", hasEnough);

            if (hasEnough) {
                var usage = tokenService.getTokenUsageForToday(userId);
                response.put("tokensRemaining", usage.getTokensRemaining());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to check token availability: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
