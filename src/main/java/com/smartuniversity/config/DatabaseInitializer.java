package com.smartuniversity.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void updateNotificationTypeConstraint() {
        try {
            logger.info("Checking and updating notifications_type_check constraint...");

            // Drop the old constraint if it exists
            jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
            logger.info("Dropped old notifications_type_check constraint");

            // Add the new constraint that includes EMERGENCY
            jdbcTemplate.execute(
                "ALTER TABLE notifications " +
                "ADD CONSTRAINT notifications_type_check " +
                "CHECK (type IN ('GENERAL', 'ACADEMIC', 'FINANCIAL_AID', 'LOST_FOUND', 'WELLNESS', 'DINING', 'LIBRARY', 'SOCIAL', 'SYSTEM', 'EMERGENCY'))"
            );
            logger.info("✅ Successfully updated notifications_type_check constraint to include EMERGENCY type");

        } catch (Exception e) {
            // Log the error but don't fail application startup
            // The constraint might already be correct or the table might not exist yet
            logger.warn("Could not update notifications_type_check constraint: " + e.getMessage());
        }
    }
}
