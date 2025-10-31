package com.smartuniversity.service;

import com.smartuniversity.model.TokenTransaction;
import com.smartuniversity.model.TokenUsage;
import com.smartuniversity.repository.TokenTransactionRepository;
import com.smartuniversity.repository.TokenUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenService {

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired
    private TokenTransactionRepository tokenTransactionRepository;

    private static final long DAILY_TOKEN_LIMIT = 500000L;

    /**
     * Get or create token usage for today
     */
    @Transactional
    public TokenUsage getOrCreateTokenUsageForToday(Long userId) {
        LocalDate today = LocalDate.now();
        Optional<TokenUsage> existing = tokenUsageRepository.findByUserIdAndUsageDate(userId, today);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new token usage for today
        TokenUsage tokenUsage = new TokenUsage(userId);
        tokenUsage.setUsageDate(today);
        tokenUsage.setDailyLimit(DAILY_TOKEN_LIMIT);
        tokenUsage.setTokensRemaining(DAILY_TOKEN_LIMIT);
        tokenUsage.setTokensUsed(0L);

        return tokenUsageRepository.save(tokenUsage);
    }

    /**
     * Check if user has enough tokens
     */
    public boolean hasEnoughTokens(Long userId, Long tokensNeeded) {
        TokenUsage usage = getOrCreateTokenUsageForToday(userId);
        return usage.getTokensRemaining() >= tokensNeeded;
    }

    /**
     * Consume tokens for a user
     */
    @Transactional
    public boolean consumeTokens(Long userId, Long tokensToConsume, TokenTransaction.TransactionType type, String description) {
        TokenUsage usage = getOrCreateTokenUsageForToday(userId);

        if (!usage.consumeTokens(tokensToConsume)) {
            // Record failed attempt
            TokenTransaction transaction = new TokenTransaction(userId, tokensToConsume, TokenTransaction.TransactionType.RATE_LIMIT_EXCEEDED, "Insufficient tokens: " + description);
            tokenTransactionRepository.save(transaction);
            return false;
        }

        // Save updated token usage
        tokenUsageRepository.save(usage);

        // Record transaction
        TokenTransaction transaction = new TokenTransaction(userId, tokensToConsume, type, description);
        tokenTransactionRepository.save(transaction);

        return true;
    }

    /**
     * Consume tokens with input and output token counts
     */
    @Transactional
    public boolean consumeTokensWithDetails(Long userId, Integer inputTokens, Integer outputTokens,
                                           TokenTransaction.TransactionType type, String description) {
        // Estimate total tokens: input + output
        Long totalTokens = (long) (inputTokens + outputTokens);

        TokenUsage usage = getOrCreateTokenUsageForToday(userId);

        if (!usage.consumeTokens(totalTokens)) {
            // Record failed attempt
            TokenTransaction transaction = new TokenTransaction(userId, totalTokens, TokenTransaction.TransactionType.RATE_LIMIT_EXCEEDED, "Insufficient tokens: " + description);
            transaction.setInputTokens(inputTokens);
            transaction.setOutputTokens(outputTokens);
            tokenTransactionRepository.save(transaction);
            return false;
        }

        // Save updated token usage
        tokenUsageRepository.save(usage);

        // Record transaction with details
        TokenTransaction transaction = new TokenTransaction(userId, totalTokens, type, description);
        transaction.setInputTokens(inputTokens);
        transaction.setOutputTokens(outputTokens);
        tokenTransactionRepository.save(transaction);

        return true;
    }

    /**
     * Get current token usage for user today
     */
    public TokenUsage getTokenUsageForToday(Long userId) {
        return getOrCreateTokenUsageForToday(userId);
    }

    /**
     * Get token statistics for a user
     */
    public Map<String, Object> getTokenStatistics(Long userId) {
        TokenUsage today = getOrCreateTokenUsageForToday(userId);
        List<TokenUsage> history = tokenUsageRepository.findByUserIdOrderByUsageDateDesc(userId);
        List<TokenTransaction> transactions = tokenTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("today", convertToMap(today));
        stats.put("dailyLimit", DAILY_TOKEN_LIMIT);
        stats.put("tokensUsed", today.getTokensUsed());
        stats.put("tokensRemaining", today.getTokensRemaining());
        stats.put("usagePercentage", (double) today.getTokensUsed() / DAILY_TOKEN_LIMIT * 100);
        stats.put("historyCount", history.size());
        stats.put("transactionCount", transactions.size());

        // Calculate average daily usage
        double avgDailyUsage = history.stream()
            .mapToLong(TokenUsage::getTokensUsed)
            .average()
            .orElse(0.0);
        stats.put("averageDailyUsage", avgDailyUsage);

        return stats;
    }

    /**
     * Get token usage history for user
     */
    public Map<String, Object> getTokenUsageHistory(Long userId, int days) {
        List<TokenUsage> history = tokenUsageRepository.findByUserIdOrderByUsageDateDesc(userId);

        // Limit to requested days
        List<TokenUsage> limited = history.stream()
            .limit(days)
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("days", days);
        result.put("count", limited.size());
        result.put("history", limited);

        return result;
    }

    /**
     * Get transaction history for user
     */
    public Map<String, Object> getTransactionHistory(Long userId, int limit) {
        List<TokenTransaction> transactions = tokenTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<TokenTransaction> limited = transactions.stream()
            .limit(limit)
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("count", limited.size());
        result.put("transactions", limited);

        return result;
    }

    /**
     * Reset daily tokens for a user
     */
    @Transactional
    public void resetDailyTokens(Long userId) {
        TokenUsage usage = getOrCreateTokenUsageForToday(userId);
        usage.resetDailyTokens();
        tokenUsageRepository.save(usage);

        // Record reset transaction
        TokenTransaction transaction = new TokenTransaction(userId, 0L, TokenTransaction.TransactionType.DAILY_RESET, "Daily token reset");
        tokenTransactionRepository.save(transaction);
    }

    /**
     * Reset tokens for all users (used by scheduler)
     */
    @Transactional
    public void resetAllUserTokens() {
        // This would be called by a scheduled task
        // For now, we'll just log it
        System.out.println("Daily token reset scheduled for all users");
    }

    /**
     * Convert TokenUsage to Map for API response
     */
    private Map<String, Object> convertToMap(TokenUsage usage) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", usage.getId());
        map.put("userId", usage.getUserId());
        map.put("usageDate", usage.getUsageDate());
        map.put("tokensUsed", usage.getTokensUsed());
        map.put("tokensRemaining", usage.getTokensRemaining());
        map.put("dailyLimit", usage.getDailyLimit());
        map.put("usagePercentage", (double) usage.getTokensUsed() / usage.getDailyLimit() * 100);
        return map;
    }
}
