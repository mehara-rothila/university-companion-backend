package com.smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class FinancialAidDonationRequest {
    
    @NotNull
    private Long financialAidId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    private boolean isAnonymous = false;
    
    @Size(max = 500)
    private String message;
    
    public FinancialAidDonationRequest() {}
    
    // Getters and Setters
    public Long getFinancialAidId() { return financialAidId; }
    public void setFinancialAidId(Long financialAidId) { this.financialAidId = financialAidId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}