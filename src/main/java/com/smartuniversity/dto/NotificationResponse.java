package com.smartuniversity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartuniversity.model.Notification;

import java.time.LocalDateTime;
import java.util.Set;

public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private Notification.NotificationType type;
    private Notification.NotificationPriority priority;
    private Notification.NotificationTarget target;
    private Set<Long> targetUserIds;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime expiresAt;
    
    private Boolean isActive;
    private String createdByName;
    private Long createdById;

    public NotificationResponse() {}

    public NotificationResponse(Notification notification) {
        this.id = notification.getId();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.type = notification.getType();
        this.priority = notification.getPriority();
        this.target = notification.getTarget();
        this.targetUserIds = notification.getTargetUserIds();
        this.createdAt = notification.getCreatedAt();
        this.expiresAt = notification.getExpiresAt();
        this.isActive = notification.getIsActive();
        if (notification.getCreatedBy() != null) {
            this.createdByName = notification.getCreatedBy().getUsername();
            this.createdById = notification.getCreatedBy().getId();
        }
    }

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

    public Notification.NotificationType getType() {
        return type;
    }

    public void setType(Notification.NotificationType type) {
        this.type = type;
    }

    public Notification.NotificationPriority getPriority() {
        return priority;
    }

    public void setPriority(Notification.NotificationPriority priority) {
        this.priority = priority;
    }

    public Notification.NotificationTarget getTarget() {
        return target;
    }

    public void setTarget(Notification.NotificationTarget target) {
        this.target = target;
    }

    public Set<Long> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(Set<Long> targetUserIds) {
        this.targetUserIds = targetUserIds;
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
}