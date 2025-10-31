package com.smartuniversity.controller;

import com.smartuniversity.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    @Autowired
    private TokenService tokenService;

    /**
     * Get current token usage for user today
     * GET /api/tokens/usage/{userId}
     */
    @GetMapping("/usage/{userId}")
    public ResponseEntity<?> getCurrentTokenUsage(@PathVariable Long userId) {
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
     * Get token statistics for user
     * GET /api/tokens/stats/{userId}
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<?> getTokenStatistics(@PathVariable Long userId) {
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
     * Get token usage history for user
     * GET /api/tokens/history/{userId}?days=7
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getTokenHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "30") int days) {
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
     * Get transaction history for user
     * GET /api/tokens/transactions/{userId}?limit=50
     */
    @GetMapping("/transactions/{userId}")
    public ResponseEntity<?> getTransactionHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "50") int limit) {
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
     * Check if user has enough tokens
     * GET /api/tokens/check/{userId}/{requiredTokens}
     */
    @GetMapping("/check/{userId}/{requiredTokens}")
    public ResponseEntity<?> checkTokenAvailability(
            @PathVariable Long userId,
            @PathVariable Long requiredTokens) {
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
