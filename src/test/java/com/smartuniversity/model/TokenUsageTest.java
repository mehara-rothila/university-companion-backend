package com.smartuniversity.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageTest {

    @Test
    void newUsage_startsWithFullDailyAllowance() {
        TokenUsage usage = new TokenUsage(1L);

        assertEquals(0L, usage.getTokensUsed());
        assertEquals(500000L, usage.getDailyLimit());
        assertEquals(500000L, usage.getTokensRemaining());
        assertNotNull(usage.getUsageDate());
    }

    @Test
    void consumeTokens_deductsFromRemainingBalance() {
        TokenUsage usage = new TokenUsage(1L);

        assertTrue(usage.consumeTokens(1000L));

        assertEquals(1000L, usage.getTokensUsed());
        assertEquals(499000L, usage.getTokensRemaining());
    }

    @Test
    void consumeTokens_allowsConsumingExactRemainingBalance() {
        TokenUsage usage = new TokenUsage(1L);
        usage.setTokensRemaining(100L);

        assertTrue(usage.consumeTokens(100L));
        assertEquals(0L, usage.getTokensRemaining());
    }

    @Test
    void consumeTokens_rejectsWhenBalanceInsufficient() {
        TokenUsage usage = new TokenUsage(1L);
        usage.setTokensUsed(499900L);
        usage.setTokensRemaining(100L);

        assertFalse(usage.consumeTokens(101L));

        // Balance must be unchanged after a rejected consumption
        assertEquals(499900L, usage.getTokensUsed());
        assertEquals(100L, usage.getTokensRemaining());
    }

    @Test
    void resetDailyTokens_restoresFullAllowanceAndStampsResetTime() {
        TokenUsage usage = new TokenUsage(1L);
        usage.consumeTokens(12345L);

        usage.resetDailyTokens();

        assertEquals(0L, usage.getTokensUsed());
        assertEquals(usage.getDailyLimit(), usage.getTokensRemaining());
        assertNotNull(usage.getResetAt());
    }
}
