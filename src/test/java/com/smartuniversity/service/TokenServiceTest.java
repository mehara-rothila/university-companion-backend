package com.smartuniversity.service;

import com.smartuniversity.model.TokenTransaction;
import com.smartuniversity.model.TokenUsage;
import com.smartuniversity.repository.TokenTransactionRepository;
import com.smartuniversity.repository.TokenUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final long DAILY_LIMIT = 500000L;
    private static final long USER_ID = 1L;

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    @Mock
    private TokenTransactionRepository tokenTransactionRepository;

    @InjectMocks
    private TokenService tokenService;

    private TokenUsage usageWith(long tokensUsed) {
        TokenUsage usage = new TokenUsage(USER_ID);
        usage.setTokensUsed(tokensUsed);
        usage.setTokensRemaining(DAILY_LIMIT - tokensUsed);
        return usage;
    }

    private void stubTodayUsage(TokenUsage usage) {
        when(tokenUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));
    }

    // ---- getOrCreateTokenUsageForToday ----

    @Test
    void getOrCreateTokenUsageForToday_returnsExistingRecord() {
        TokenUsage existing = usageWith(1000L);
        stubTodayUsage(existing);

        TokenUsage result = tokenService.getOrCreateTokenUsageForToday(USER_ID);

        assertSame(existing, result);
        verify(tokenUsageRepository, never()).save(any());
    }

    @Test
    void getOrCreateTokenUsageForToday_createsNewRecordWithDefaults() {
        when(tokenUsageRepository.findByUserIdAndUsageDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(tokenUsageRepository.save(any(TokenUsage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TokenUsage result = tokenService.getOrCreateTokenUsageForToday(USER_ID);

        assertEquals(USER_ID, result.getUserId());
        assertEquals(LocalDate.now(), result.getUsageDate());
        assertEquals(0L, result.getTokensUsed());
        assertEquals(DAILY_LIMIT, result.getDailyLimit());
        assertEquals(DAILY_LIMIT, result.getTokensRemaining());
    }

    // ---- hasEnoughTokens ----

    @Test
    void hasEnoughTokens_trueWhenRemainingCoversRequest() {
        stubTodayUsage(usageWith(DAILY_LIMIT - 500L));

        assertTrue(tokenService.hasEnoughTokens(USER_ID, 500L));
    }

    @Test
    void hasEnoughTokens_falseWhenRequestExceedsRemaining() {
        stubTodayUsage(usageWith(DAILY_LIMIT - 100L));

        assertFalse(tokenService.hasEnoughTokens(USER_ID, 200L));
    }

    // ---- consumeTokens ----

    @Test
    void consumeTokens_success_updatesUsageAndRecordsTransaction() {
        TokenUsage usage = usageWith(0L);
        stubTodayUsage(usage);

        boolean result = tokenService.consumeTokens(
                USER_ID, 400L, TokenTransaction.TransactionType.CHAT, "chat message");

        assertTrue(result);
        assertEquals(400L, usage.getTokensUsed());
        assertEquals(DAILY_LIMIT - 400L, usage.getTokensRemaining());
        verify(tokenUsageRepository).save(usage);

        ArgumentCaptor<TokenTransaction> captor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionRepository).save(captor.capture());
        TokenTransaction transaction = captor.getValue();
        assertEquals(400L, transaction.getTokensConsumed());
        assertEquals(TokenTransaction.TransactionType.CHAT, transaction.getType());
        assertEquals("chat message", transaction.getDescription());
    }

    @Test
    void consumeTokens_insufficientBalance_recordsRateLimitTransaction() {
        TokenUsage usage = usageWith(DAILY_LIMIT - 100L);
        stubTodayUsage(usage);

        boolean result = tokenService.consumeTokens(
                USER_ID, 200L, TokenTransaction.TransactionType.CHAT, "chat message");

        assertFalse(result);
        // Usage must not change and must not be saved
        assertEquals(100L, usage.getTokensRemaining());
        verify(tokenUsageRepository, never()).save(any());

        ArgumentCaptor<TokenTransaction> captor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionRepository).save(captor.capture());
        assertEquals(TokenTransaction.TransactionType.RATE_LIMIT_EXCEEDED, captor.getValue().getType());
        assertTrue(captor.getValue().getDescription().startsWith("Insufficient tokens"));
    }

    @Test
    void consumeTokens_rejectsZeroNegativeAndNullAmounts() {
        assertThrows(IllegalArgumentException.class, () ->
                tokenService.consumeTokens(USER_ID, 0L, TokenTransaction.TransactionType.CHAT, "x"));
        assertThrows(IllegalArgumentException.class, () ->
                tokenService.consumeTokens(USER_ID, -5L, TokenTransaction.TransactionType.CHAT, "x"));
        assertThrows(IllegalArgumentException.class, () ->
                tokenService.consumeTokens(USER_ID, null, TokenTransaction.TransactionType.CHAT, "x"));
        verifyNoInteractions(tokenUsageRepository, tokenTransactionRepository);
    }

    // ---- consumeTokensWithDetails ----

    @Test
    void consumeTokensWithDetails_success_recordsInputAndOutputTokens() {
        TokenUsage usage = usageWith(0L);
        stubTodayUsage(usage);

        boolean result = tokenService.consumeTokensWithDetails(
                USER_ID, 100, 50, TokenTransaction.TransactionType.CHAT, "chat");

        assertTrue(result);
        assertEquals(150L, usage.getTokensUsed());
        verify(tokenUsageRepository).save(usage);

        ArgumentCaptor<TokenTransaction> captor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionRepository).save(captor.capture());
        TokenTransaction transaction = captor.getValue();
        assertEquals(150L, transaction.getTokensConsumed());
        assertEquals(100, transaction.getInputTokens());
        assertEquals(50, transaction.getOutputTokens());
    }

    @Test
    void consumeTokensWithDetails_insufficientBalance_recordsFailedAttempt() {
        TokenUsage usage = usageWith(DAILY_LIMIT - 100L);
        stubTodayUsage(usage);

        boolean result = tokenService.consumeTokensWithDetails(
                USER_ID, 100, 50, TokenTransaction.TransactionType.CHAT, "chat");

        assertFalse(result);
        verify(tokenUsageRepository, never()).save(any());

        ArgumentCaptor<TokenTransaction> captor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionRepository).save(captor.capture());
        TokenTransaction transaction = captor.getValue();
        assertEquals(TokenTransaction.TransactionType.RATE_LIMIT_EXCEEDED, transaction.getType());
        assertEquals(100, transaction.getInputTokens());
        assertEquals(50, transaction.getOutputTokens());
    }

    // ---- statistics and history ----

    @Test
    void getTokenStatistics_computesUsageAndAverages() {
        TokenUsage today = usageWith(1000L);
        stubTodayUsage(today);
        when(tokenUsageRepository.findByUserIdOrderByUsageDateDesc(USER_ID))
                .thenReturn(List.of(usageWith(1000L), usageWith(3000L)));
        when(tokenTransactionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(new TokenTransaction(), new TokenTransaction(), new TokenTransaction()));

        Map<String, Object> stats = tokenService.getTokenStatistics(USER_ID);

        assertEquals(DAILY_LIMIT, stats.get("dailyLimit"));
        assertEquals(1000L, stats.get("tokensUsed"));
        assertEquals(DAILY_LIMIT - 1000L, stats.get("tokensRemaining"));
        assertEquals(0.2, (double) stats.get("usagePercentage"), 0.0001);
        assertEquals(2, stats.get("historyCount"));
        assertEquals(3, stats.get("transactionCount"));
        assertEquals(2000.0, (double) stats.get("averageDailyUsage"), 0.0001);
    }

    @Test
    void getTokenUsageHistory_limitsToRequestedDays() {
        when(tokenUsageRepository.findByUserIdOrderByUsageDateDesc(USER_ID))
                .thenReturn(List.of(usageWith(1L), usageWith(2L), usageWith(3L), usageWith(4L), usageWith(5L)));

        Map<String, Object> result = tokenService.getTokenUsageHistory(USER_ID, 3);

        assertEquals(3, result.get("count"));
        assertEquals(3, ((List<?>) result.get("history")).size());
    }

    @Test
    void getTransactionHistory_limitsToRequestedCount() {
        when(tokenTransactionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(new TokenTransaction(), new TokenTransaction(), new TokenTransaction()));

        Map<String, Object> result = tokenService.getTransactionHistory(USER_ID, 2);

        assertEquals(2, result.get("count"));
        assertEquals(2, ((List<?>) result.get("transactions")).size());
    }

    // ---- resetDailyTokens ----

    @Test
    void resetDailyTokens_restoresFullBalanceAndRecordsReset() {
        TokenUsage usage = usageWith(400L);
        stubTodayUsage(usage);

        tokenService.resetDailyTokens(USER_ID);

        assertEquals(0L, usage.getTokensUsed());
        assertEquals(DAILY_LIMIT, usage.getTokensRemaining());
        assertNotNull(usage.getResetAt());
        verify(tokenUsageRepository).save(usage);

        ArgumentCaptor<TokenTransaction> captor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionRepository).save(captor.capture());
        assertEquals(TokenTransaction.TransactionType.DAILY_RESET, captor.getValue().getType());
        assertEquals(0L, captor.getValue().getTokensConsumed());
    }
}
