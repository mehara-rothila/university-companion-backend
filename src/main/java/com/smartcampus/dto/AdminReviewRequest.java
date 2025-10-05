package com.smartcampus.dto;

import com.smartcampus.model.FinancialAid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public class AdminReviewRequest {
    
    @NotNull
    private FinancialAid.ApplicationStatus status;
    
    @DecimalMin("0.0")
    private BigDecimal approvedAmount;
    
    @Size(max = 1000)
    private String adminNotes;
    
    @Size(max = 500)
    private String rejectionReason;
    
    private boolean isDonationEligible = false;
    
    public AdminReviewRequest() {}
    
    // Getters and Setters
    public FinancialAid.ApplicationStatus getStatus() { return status; }
    public void setStatus(FinancialAid.ApplicationStatus status) { this.status = status; }
    
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    
    public boolean isDonationEligible() { return isDonationEligible; }
    public void setDonationEligible(boolean donationEligible) { isDonationEligible = donationEligible; }
}