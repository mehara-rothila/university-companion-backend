package com.smartcampus.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "financial_aid_applications")
public class FinancialAid {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 200)
    @Column(length = 200, columnDefinition = "VARCHAR(200)")
    private String title;
    
    @NotBlank
    @Size(max = 2000)
    @Column(length = 2000, columnDefinition = "TEXT")
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private AidType aidType;
    
    @NotBlank
    @Size(max = 100)
    @Column(length = 100, columnDefinition = "VARCHAR(100)")
    private String category;
    
    @NotNull
    @DecimalMin("0.0")
    @Column(precision = 10, scale = 2)
    private BigDecimal requestedAmount;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal approvedAmount;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.PENDING;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private Urgency urgency = Urgency.MEDIUM;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isAnonymous = false;
    
    @Size(max = 500)
    @Column(length = 500, columnDefinition = "VARCHAR(500)")
    private String supportingDocuments;
    
    @Size(max = 1000)
    @Column(length = 1000, columnDefinition = "TEXT")
    private String personalStory;
    
    @Size(max = 1000)
    @Column(length = 1000, columnDefinition = "TEXT")
    private String adminNotes;
    
    @Size(max = 500)
    @Column(length = 500, columnDefinition = "TEXT")
    private String rejectionReason;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User applicant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    
    private LocalDateTime reviewedAt;
    
    private LocalDateTime applicationDeadline;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isDonationEligible = false;
    
    @DecimalMin("0.0")
    @Column(precision = 10, scale = 2)
    private BigDecimal raisedAmount = BigDecimal.ZERO;
    
    private Integer supporterCount = 0;
    
    public enum AidType {
        SCHOLARSHIP, GRANT, EMERGENCY_FUND, LOAN, WORK_STUDY, CUSTOM
    }
    
    public enum ApplicationStatus {
        DRAFT, PENDING, UNDER_REVIEW, APPROVED, REJECTED, FUNDED, EXPIRED
    }
    
    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum Urgency {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public FinancialAid() {}
    
    public FinancialAid(String title, String description, AidType aidType, 
                       String category, BigDecimal requestedAmount, User applicant) {
        this.title = title;
        this.description = description;
        this.aidType = aidType;
        this.category = category;
        this.requestedAmount = requestedAmount;
        this.applicant = applicant;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public AidType getAidType() { return aidType; }
    public void setAidType(AidType aidType) { this.aidType = aidType; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    
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
    
    public User getApplicant() { return applicant; }
    public void setApplicant(User applicant) { this.applicant = applicant; }
    
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    
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