package com.smartuniversity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_aid_donations")
public class FinancialAidDonation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_aid_id")
    private FinancialAid financialAid;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private User donor;
    
    @NotNull
    @DecimalMin("0.01")
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isAnonymous = false;
    
    @Column(length = 500, columnDefinition = "VARCHAR(500)")
    private String message;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private DonationStatus status = DonationStatus.COMPLETED;
    
    private String transactionId;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum DonationStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
    
    public FinancialAidDonation() {}
    
    public FinancialAidDonation(FinancialAid financialAid, User donor, BigDecimal amount) {
        this.financialAid = financialAid;
        this.donor = donor;
        this.amount = amount;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public FinancialAid getFinancialAid() { return financialAid; }
    public void setFinancialAid(FinancialAid financialAid) { this.financialAid = financialAid; }
    
    public User getDonor() { return donor; }
    public void setDonor(User donor) { this.donor = donor; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public DonationStatus getStatus() { return status; }
    public void setStatus(DonationStatus status) { this.status = status; }
    
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}