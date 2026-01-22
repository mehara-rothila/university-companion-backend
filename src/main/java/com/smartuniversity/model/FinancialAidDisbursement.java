package com.smartuniversity.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_aid_disbursements")
public class FinancialAidDisbursement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "financial_aid_id", nullable = false)
    private FinancialAid financialAid;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisbursementStatus status = DisbursementStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisbursementMethod method; // BANK_TRANSFER, CASH, CHEQUE, DIGITAL_WALLET

    @Column(length = 500)
    private String transactionReference; // Transaction ID or reference number

    @Column(length = 500)
    private String bankDetails; // Bank account info for transfers

    @Column(name = "disbursed_by")
    private Long disbursedBy; // Admin who processed the disbursement

    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DisbursementStatus {
        PENDING,        // Scheduled but not yet disbursed
        PROCESSING,     // In progress
        COMPLETED,      // Successfully disbursed
        FAILED,         // Failed to disburse
        CANCELLED       // Cancelled by admin
    }

    public enum DisbursementMethod {
        BANK_TRANSFER,
        CASH,
        CHEQUE,
        DIGITAL_WALLET,
        OTHER
    }

    // Constructors
    public FinancialAidDisbursement() {
    }

    public FinancialAidDisbursement(FinancialAid financialAid, BigDecimal amount, DisbursementMethod method) {
        this.financialAid = financialAid;
        this.amount = amount;
        this.method = method;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FinancialAid getFinancialAid() {
        return financialAid;
    }

    public void setFinancialAid(FinancialAid financialAid) {
        this.financialAid = financialAid;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public DisbursementStatus getStatus() {
        return status;
    }

    public void setStatus(DisbursementStatus status) {
        this.status = status;
    }

    public DisbursementMethod getMethod() {
        return method;
    }

    public void setMethod(DisbursementMethod method) {
        this.method = method;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getBankDetails() {
        return bankDetails;
    }

    public void setBankDetails(String bankDetails) {
        this.bankDetails = bankDetails;
    }

    public Long getDisbursedBy() {
        return disbursedBy;
    }

    public void setDisbursedBy(Long disbursedBy) {
        this.disbursedBy = disbursedBy;
    }

    public LocalDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(LocalDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public LocalDateTime getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDateTime scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
