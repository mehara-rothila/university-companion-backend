package com.smartuniversity.dto;

import com.smartuniversity.model.FinancialAid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinancialAidRequest {
    
    @NotBlank
    @Size(max = 200)
    private String title;
    
    @NotBlank
    @Size(max = 2000)
    private String description;
    
    @NotNull
    private FinancialAid.AidType aidType;
    
    @NotBlank
    @Size(max = 100)
    private String category;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal requestedAmount;
    
    private FinancialAid.Priority priority = FinancialAid.Priority.MEDIUM;
    
    private FinancialAid.Urgency urgency = FinancialAid.Urgency.MEDIUM;
    
    private boolean isAnonymous = false;
    
    @Size(max = 500)
    private String supportingDocuments;
    
    @Size(max = 1000)
    private String personalStory;
    
    private LocalDateTime applicationDeadline;
    
    private boolean isDonationEligible = false;
    
    public FinancialAidRequest() {}
    
    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public FinancialAid.AidType getAidType() { return aidType; }
    public void setAidType(FinancialAid.AidType aidType) { this.aidType = aidType; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    
    public FinancialAid.Priority getPriority() { return priority; }
    public void setPriority(FinancialAid.Priority priority) { this.priority = priority; }
    
    public FinancialAid.Urgency getUrgency() { return urgency; }
    public void setUrgency(FinancialAid.Urgency urgency) { this.urgency = urgency; }
    
    public boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(boolean anonymous) { isAnonymous = anonymous; }
    
    public String getSupportingDocuments() { return supportingDocuments; }
    public void setSupportingDocuments(String supportingDocuments) { this.supportingDocuments = supportingDocuments; }
    
    public String getPersonalStory() { return personalStory; }
    public void setPersonalStory(String personalStory) { this.personalStory = personalStory; }
    
    public LocalDateTime getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(LocalDateTime applicationDeadline) { this.applicationDeadline = applicationDeadline; }
    
    public boolean getIsDonationEligible() { return isDonationEligible; }
    public void setIsDonationEligible(boolean donationEligible) { isDonationEligible = donationEligible; }
}