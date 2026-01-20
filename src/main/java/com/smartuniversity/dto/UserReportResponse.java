package com.smartuniversity.dto;

import com.smartuniversity.model.UserReport;
import java.time.LocalDateTime;

public class UserReportResponse {

    private Long id;
    private Long reporterId;
    private String reporterUsername;
    private String reporterFullName;
    private String reporterImage;
    private Long reportedUserId;
    private String reportedUsername;
    private String reportedFullName;
    private String reportedUserImage;
    private Long conversationId;
    private String reason;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private Long reviewedById;
    private String reviewedByUsername;
    private String adminNotes;

    public UserReportResponse() {}

    public UserReportResponse(UserReport report) {
        this.id = report.getId();

        // Reporter info
        if (report.getReporter() != null) {
            this.reporterId = report.getReporter().getId();
            this.reporterUsername = report.getReporter().getUsername();
            String firstName = report.getReporter().getFirstName() != null ? report.getReporter().getFirstName() : "";
            String lastName = report.getReporter().getLastName() != null ? report.getReporter().getLastName() : "";
            this.reporterFullName = (firstName + " " + lastName).trim();
            this.reporterImage = report.getReporter().getImageUrl();
        }

        // Reported user info
        if (report.getReportedUser() != null) {
            this.reportedUserId = report.getReportedUser().getId();
            this.reportedUsername = report.getReportedUser().getUsername();
            String firstName = report.getReportedUser().getFirstName() != null ? report.getReportedUser().getFirstName() : "";
            String lastName = report.getReportedUser().getLastName() != null ? report.getReportedUser().getLastName() : "";
            this.reportedFullName = (firstName + " " + lastName).trim();
            this.reportedUserImage = report.getReportedUser().getImageUrl();
        }

        // Conversation reference
        if (report.getConversation() != null) {
            this.conversationId = report.getConversation().getId();
        }

        this.reason = report.getReason() != null ? report.getReason().toString() : null;
        this.description = report.getDescription();
        this.status = report.getStatus() != null ? report.getStatus().toString() : null;
        this.createdAt = report.getCreatedAt();
        this.reviewedAt = report.getReviewedAt();

        // Reviewer info
        if (report.getReviewedBy() != null) {
            this.reviewedById = report.getReviewedBy().getId();
            this.reviewedByUsername = report.getReviewedBy().getUsername();
        }

        this.adminNotes = report.getAdminNotes();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }

    public String getReporterUsername() { return reporterUsername; }
    public void setReporterUsername(String reporterUsername) { this.reporterUsername = reporterUsername; }

    public String getReporterFullName() { return reporterFullName; }
    public void setReporterFullName(String reporterFullName) { this.reporterFullName = reporterFullName; }

    public String getReporterImage() { return reporterImage; }
    public void setReporterImage(String reporterImage) { this.reporterImage = reporterImage; }

    public Long getReportedUserId() { return reportedUserId; }
    public void setReportedUserId(Long reportedUserId) { this.reportedUserId = reportedUserId; }

    public String getReportedUsername() { return reportedUsername; }
    public void setReportedUsername(String reportedUsername) { this.reportedUsername = reportedUsername; }

    public String getReportedFullName() { return reportedFullName; }
    public void setReportedFullName(String reportedFullName) { this.reportedFullName = reportedFullName; }

    public String getReportedUserImage() { return reportedUserImage; }
    public void setReportedUserImage(String reportedUserImage) { this.reportedUserImage = reportedUserImage; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getReviewedById() { return reviewedById; }
    public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }

    public String getReviewedByUsername() { return reviewedByUsername; }
    public void setReviewedByUsername(String reviewedByUsername) { this.reviewedByUsername = reviewedByUsername; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
