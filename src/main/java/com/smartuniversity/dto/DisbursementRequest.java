package com.smartuniversity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DisbursementRequest {
    private Long financialAidId;
    private BigDecimal amount;
    private String method; // BANK_TRANSFER, CASH, CHEQUE, DIGITAL_WALLET, OTHER
    private String transactionReference;
    private String bankDetails;
    private LocalDateTime scheduledDate;
    private String notes;

    public DisbursementRequest() {
    }

    // Getters and Setters
    public Long getFinancialAidId() {
        return financialAidId;
    }

    public void setFinancialAidId(Long financialAidId) {
        this.financialAidId = financialAidId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
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
}
