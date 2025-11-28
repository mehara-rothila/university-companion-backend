package com.smartuniversity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartuniversity.model.Notification;
import java.time.LocalDateTime;

public class EmergencyNotificationResponse {
    private Long id;
    private String title;
    private String message;
    private String type;
    private String priority;
    private String target;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime expiresAt;

    private Boolean isActive;
    private String createdByName;
    private Long createdById;
    private Long totalUsers;
    private Long seenCount;
    private Long dismissedCount;
    private Boolean currentUserDismissed;
    private Boolean currentUserSeen;

    public EmergencyNotificationResponse() {}

    public EmergencyNotificationResponse(Notification notification,
                                       Long seenCount,
                                       Long dismissedCount,
                                       Boolean currentUserDismissed,
                                       Boolean currentUserSeen,
                                       Long totalUsers) {
        this.id = notification.getId();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.type = notification.getType().toString();
        this.priority = notification.getPriority().toString();
        this.target = notification.getTarget().toString();
        this.createdAt = notification.getCreatedAt();
        this.expiresAt = notification.getExpiresAt();
        this.isActive = notification.getIsActive();
        this.createdByName = notification.getCreatedBy() != null ?
                notification.getCreatedBy().getFirstName() + " " + notification.getCreatedBy().getLastName() : "Unknown";
        this.createdById = notification.getCreatedBy() != null ? notification.getCreatedBy().getId() : null;
        this.seenCount = seenCount;
        this.dismissedCount = dismissedCount;
        this.currentUserDismissed = currentUserDismissed;
        this.currentUserSeen = currentUserSeen;
        this.totalUsers = totalUsers;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public Long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Long getSeenCount() {
        return seenCount;
    }

    public void setSeenCount(Long seenCount) {
        this.seenCount = seenCount;
    }

    public Long getDismissedCount() {
        return dismissedCount;
    }

    public void setDismissedCount(Long dismissedCount) {
        this.dismissedCount = dismissedCount;
    }

    public Boolean getCurrentUserDismissed() {
        return currentUserDismissed;
    }

    public void setCurrentUserDismissed(Boolean currentUserDismissed) {
        this.currentUserDismissed = currentUserDismissed;
    }

    public Boolean getCurrentUserSeen() {
        return currentUserSeen;
    }

    public void setCurrentUserSeen(Boolean currentUserSeen) {
        this.currentUserSeen = currentUserSeen;
    }
}
