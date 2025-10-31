package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_usage", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "usage_date"})
})
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false)
    private Long tokensUsed = 0L;

    @Column(nullable = false)
    private Long dailyLimit = 500000L;

    @Column(nullable = false)
    private Long tokensRemaining = 500000L;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime resetAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (usageDate == null) {
            usageDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public TokenUsage() {}

    public TokenUsage(Long userId) {
        this.userId = userId;
        this.usageDate = LocalDate.now();
        this.tokensUsed = 0L;
        this.dailyLimit = 500000L;
        this.tokensRemaining = 500000L;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public Long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public Long getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(Long dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public Long getTokensRemaining() {
        return tokensRemaining;
    }

    public void setTokensRemaining(Long tokensRemaining) {
        this.tokensRemaining = tokensRemaining;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getResetAt() {
        return resetAt;
    }

    public void setResetAt(LocalDateTime resetAt) {
        this.resetAt = resetAt;
    }

    // Helper method to consume tokens
    public boolean consumeTokens(Long tokensToConsume) {
        if (tokensToConsume > tokensRemaining) {
            return false;
        }
        this.tokensUsed += tokensToConsume;
        this.tokensRemaining -= tokensToConsume;
        return true;
    }

    // Helper method to reset daily tokens
    public void resetDailyTokens() {
        this.tokensUsed = 0L;
        this.tokensRemaining = this.dailyLimit;
        this.resetAt = LocalDateTime.now();
    }
}
