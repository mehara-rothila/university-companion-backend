package com.smartuniversity.scheduler;

import com.smartuniversity.model.TokenUsage;
import com.smartuniversity.repository.TokenUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@EnableScheduling
public class TokenScheduler {

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    /**
     * Reset all users' daily tokens at midnight (00:00:00)
     * Runs every day at midnight
     */
    @Scheduled(cron = "0 0 0 * * *")  // Midnight every day
    @Transactional
    public void resetDailyTokensForAllUsers() {
        System.out.println("🕐 Starting daily token reset for all users...");

        try {
            // Get all token usage records from yesterday or before
            LocalDate today = LocalDate.now();
            List<TokenUsage> allTokenUsages = tokenUsageRepository.findAll();

            int resetCount = 0;
            for (TokenUsage usage : allTokenUsages) {
                // If the usage date is not today, reset it
                if (!usage.getUsageDate().equals(today)) {
                    usage.setUsageDate(today);
                    usage.setTokensUsed(0L);
                    usage.setTokensRemaining(500000L); // Should match TokenService.DAILY_TOKEN_LIMIT
                    usage.setResetAt(java.time.LocalDateTime.now());
                    tokenUsageRepository.save(usage);
                    resetCount++;
                }
            }

            System.out.println("✅ Daily token reset completed. Reset " + resetCount + " users' tokens.");
        } catch (Exception e) {
            System.err.println("❌ Error during daily token reset: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
