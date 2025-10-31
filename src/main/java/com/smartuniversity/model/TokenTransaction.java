package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_transactions")
public class TokenTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long tokensConsumed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String description;

    @Column
    private Integer inputTokens;

    @Column
    private Integer outputTokens;

    @Column
    private String requestId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Transaction types
    public enum TransactionType {
        CHAT,           // Regular chat message
        IMAGE_ANALYSIS, // Image file analysis
        PDF_ANALYSIS,   // PDF file analysis
        RATE_LIMIT_EXCEEDED,
        MANUAL_RESET,
        DAILY_RESET
    }

    // Constructors
    public TokenTransaction() {}

    public TokenTransaction(Long userId, Long tokensConsumed, TransactionType type, String description) {
        this.userId = userId;
        this.tokensConsumed = tokensConsumed;
        this.type = type;
        this.description = description;
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

    public Long getTokensConsumed() {
        return tokensConsumed;
    }

    public void setTokensConsumed(Long tokensConsumed) {
        this.tokensConsumed = tokensConsumed;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
