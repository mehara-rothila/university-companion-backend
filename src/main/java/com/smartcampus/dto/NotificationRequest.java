package com.smartcampus.dto;

import com.smartcampus.model.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

public class NotificationRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String message;

    @NotNull
    private Notification.NotificationType type;

    @NotNull
    private Notification.NotificationPriority priority;

    @NotNull
    private Notification.NotificationTarget target;

    private Set<Long> targetUserIds;

    private LocalDateTime expiresAt;

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

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}