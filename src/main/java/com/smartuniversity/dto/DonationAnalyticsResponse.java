package com.smartuniversity.dto;

import java.math.BigDecimal;
import java.util.List;

public class DonationAnalyticsResponse {

    private BigDecimal totalDonated;
    private BigDecimal averageDonation;
    private Long totalDonationCount;
    private Long uniqueDonorCount;
    private List<TopDonorItem> topDonors;
    private List<RecentDonationItem> recentDonations;
    private List<CategoryBreakdownItem> donationsByCategory;
    private List<AidTypeBreakdownItem> donationsByAidType;
    private List<FundraisingProgressItem> fundraisingProgress;

    public DonationAnalyticsResponse() {}

    // ---- Inner static classes ----

    public static class TopDonorItem {
        private Long donorId;
        private String donorName;
        private String email;
        private BigDecimal totalAmount;
        private Long donationCount;

        public TopDonorItem() {}

        public Long getDonorId() { return donorId; }
        public void setDonorId(Long donorId) { this.donorId = donorId; }
        public String getDonorName() { return donorName; }
        public void setDonorName(String donorName) { this.donorName = donorName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public Long getDonationCount() { return donationCount; }
        public void setDonationCount(Long donationCount) { this.donationCount = donationCount; }
    }

    public static class RecentDonationItem {
        private Long donationId;
        private String donorName;
        private BigDecimal amount;
        private String applicationTitle;
        private Long applicationId;
        private String category;
        private String message;
        private String status;
        private String createdAt;

        public RecentDonationItem() {}

        public Long getDonationId() { return donationId; }
        public void setDonationId(Long donationId) { this.donationId = donationId; }
        public String getDonorName() { return donorName; }
        public void setDonorName(String donorName) { this.donorName = donorName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getApplicationTitle() { return applicationTitle; }
        public void setApplicationTitle(String applicationTitle) { this.applicationTitle = applicationTitle; }
        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class CategoryBreakdownItem {
        private String category;
        private BigDecimal totalAmount;
        private Long donationCount;

        public CategoryBreakdownItem() {}

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public Long getDonationCount() { return donationCount; }
        public void setDonationCount(Long donationCount) { this.donationCount = donationCount; }
    }

    public static class AidTypeBreakdownItem {
        private String aidType;
        private BigDecimal totalAmount;
        private Long donationCount;

        public AidTypeBreakdownItem() {}

        public String getAidType() { return aidType; }
        public void setAidType(String aidType) { this.aidType = aidType; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public Long getDonationCount() { return donationCount; }
        public void setDonationCount(Long donationCount) { this.donationCount = donationCount; }
    }

    public static class FundraisingProgressItem {
        private Long applicationId;
        private String title;
        private String category;
        private String aidType;
        private BigDecimal requestedAmount;
        private BigDecimal raisedAmount;
        private Integer supporterCount;
        private double progressPercent;

        public FundraisingProgressItem() {}

        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getAidType() { return aidType; }
        public void setAidType(String aidType) { this.aidType = aidType; }
        public BigDecimal getRequestedAmount() { return requestedAmount; }
        public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
        public BigDecimal getRaisedAmount() { return raisedAmount; }
        public void setRaisedAmount(BigDecimal raisedAmount) { this.raisedAmount = raisedAmount; }
        public Integer getSupporterCount() { return supporterCount; }
        public void setSupporterCount(Integer supporterCount) { this.supporterCount = supporterCount; }
        public double getProgressPercent() { return progressPercent; }
        public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }
    }

    // ---- Outer class getters and setters ----

    public BigDecimal getTotalDonated() { return totalDonated; }
    public void setTotalDonated(BigDecimal totalDonated) { this.totalDonated = totalDonated; }
    public BigDecimal getAverageDonation() { return averageDonation; }
    public void setAverageDonation(BigDecimal averageDonation) { this.averageDonation = averageDonation; }
    public Long getTotalDonationCount() { return totalDonationCount; }
    public void setTotalDonationCount(Long totalDonationCount) { this.totalDonationCount = totalDonationCount; }
    public Long getUniqueDonorCount() { return uniqueDonorCount; }
    public void setUniqueDonorCount(Long uniqueDonorCount) { this.uniqueDonorCount = uniqueDonorCount; }
    public List<TopDonorItem> getTopDonors() { return topDonors; }
    public void setTopDonors(List<TopDonorItem> topDonors) { this.topDonors = topDonors; }
    public List<RecentDonationItem> getRecentDonations() { return recentDonations; }
    public void setRecentDonations(List<RecentDonationItem> recentDonations) { this.recentDonations = recentDonations; }
    public List<CategoryBreakdownItem> getDonationsByCategory() { return donationsByCategory; }
    public void setDonationsByCategory(List<CategoryBreakdownItem> donationsByCategory) { this.donationsByCategory = donationsByCategory; }
    public List<AidTypeBreakdownItem> getDonationsByAidType() { return donationsByAidType; }
    public void setDonationsByAidType(List<AidTypeBreakdownItem> donationsByAidType) { this.donationsByAidType = donationsByAidType; }
    public List<FundraisingProgressItem> getFundraisingProgress() { return fundraisingProgress; }
    public void setFundraisingProgress(List<FundraisingProgressItem> fundraisingProgress) { this.fundraisingProgress = fundraisingProgress; }
}
