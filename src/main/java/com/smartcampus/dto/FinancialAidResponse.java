package com.smartcampus.dto;

import com.smartcampus.model.FinancialAid;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinancialAidResponse {
    
    private Long id;
    private String title;
    private String description;
    private FinancialAid.AidType aidType;
    private String category;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private FinancialAid.ApplicationStatus status;
    private FinancialAid.Priority priority;
    private FinancialAid.Urgency urgency;
    private boolean isAnonymous;
    private String supportingDocuments;
    private String personalStory;
    private String adminNotes;
    private String rejectionReason;
    private String applicantName;
    private Long applicantId;
    private String reviewedByName;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applicationDeadline;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    private boolean isDonationEligible;
    private BigDecimal raisedAmount;
    private Integer supporterCount;
    
    public FinancialAidResponse() {}
    
    public FinancialAidResponse(FinancialAid financialAid) {
        this.id = financialAid.getId();
        this.title = financialAid.getTitle();
        this.description = financialAid.getDescription();
        this.aidType = financialAid.getAidType();
        this.category = financialAid.getCategory();
        this.requestedAmount = financialAid.getRequestedAmount();
        this.approvedAmount = financialAid.getApprovedAmount();
        this.status = financialAid.getStatus();
        this.priority = financialAid.getPriority();
        this.urgency = financialAid.getUrgency();
        this.isAnonymous = financialAid.isAnonymous();
        this.supportingDocuments = financialAid.getSupportingDocuments();
        this.personalStory = financialAid.getPersonalStory();
        this.adminNotes = financialAid.getAdminNotes();
        this.rejectionReason = financialAid.getRejectionReason();
        this.reviewedAt = financialAid.getReviewedAt();
        this.applicationDeadline = financialAid.getApplicationDeadline();
        this.createdAt = financialAid.getCreatedAt();
        this.updatedAt = financialAid.getUpdatedAt();
        this.isDonationEligible = financialAid.isDonationEligible();
        this.raisedAmount = financialAid.getRaisedAmount();
        this.supporterCount = financialAid.getSupporterCount();
        
        if (financialAid.getApplicant() != null) {
            this.applicantId = financialAid.getApplicant().getId();
            if (!financialAid.isAnonymous()) {
                this.applicantName = financialAid.getApplicant().getUsername();
            }
        }
        
        if (financialAid.getReviewedBy() != null) {
            this.reviewedByName = financialAid.getReviewedBy().getUsername();
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
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
    
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    
    public FinancialAid.ApplicationStatus getStatus() { return status; }
    public void setStatus(FinancialAid.ApplicationStatus status) { this.status = status; }
    
    public FinancialAid.Priority getPriority() { return priority; }
    public void setPriority(FinancialAid.Priority priority) { this.priority = priority; }
    
    public FinancialAid.Urgency getUrgency() { return urgency; }
    public void setUrgency(FinancialAid.Urgency urgency) { this.urgency = urgency; }
    
    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }
    
    public String getSupportingDocuments() { return supportingDocuments; }
    public void setSupportingDocuments(String supportingDocuments) { this.supportingDocuments = supportingDocuments; }
    
    public String getPersonalStory() { return personalStory; }
    public void setPersonalStory(String personalStory) { this.personalStory = personalStory; }
    
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    
    public LocalDateTime getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(LocalDateTime applicationDeadline) { this.applicationDeadline = applicationDeadline; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isDonationEligible() { return isDonationEligible; }
    public void setDonationEligible(boolean donationEligible) { isDonationEligible = donationEligible; }
    
    public BigDecimal getRaisedAmount() { return raisedAmount; }
    public void setRaisedAmount(BigDecimal raisedAmount) { this.raisedAmount = raisedAmount; }
    
    public Integer getSupporterCount() { return supporterCount; }
    public void setSupporterCount(Integer supporterCount) { this.supporterCount = supporterCount; }
}