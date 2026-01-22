package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notification_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // Categories the user wants to receive (defaults to all enabled)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_preference_enabled_types",
                     joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "notification_type")
    @Enumerated(EnumType.STRING)
    private Set<Notification.NotificationType> enabledTypes = new HashSet<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Enable all types by default (except EMERGENCY which is always on)
        if (enabledTypes.isEmpty()) {
            enabledTypes.add(Notification.NotificationType.GENERAL);
            enabledTypes.add(Notification.NotificationType.ACADEMIC);
            enabledTypes.add(Notification.NotificationType.FINANCIAL_AID);
            enabledTypes.add(Notification.NotificationType.LOST_FOUND);
            enabledTypes.add(Notification.NotificationType.WELLNESS);
            enabledTypes.add(Notification.NotificationType.DINING);
            enabledTypes.add(Notification.NotificationType.LIBRARY);
            enabledTypes.add(Notification.NotificationType.SOCIAL);
            enabledTypes.add(Notification.NotificationType.SYSTEM);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public NotificationPreference() {}

    public NotificationPreference(Long userId) {
        this.userId = userId;
    }

    // Check if user wants to receive a notification type
    // EMERGENCY is always delivered regardless of preferences
    public boolean shouldReceive(Notification.NotificationType type) {
        if (type == Notification.NotificationType.EMERGENCY) {
            return true; // Emergency notifications always delivered
        }
        return enabledTypes.contains(type);
    }

    // Toggle a notification type
    public void toggleType(Notification.NotificationType type, boolean enabled) {
        if (type == Notification.NotificationType.EMERGENCY) {
            return; // Cannot disable emergency notifications
        }
        if (enabled) {
            enabledTypes.add(type);
        } else {
            enabledTypes.remove(type);
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Set<Notification.NotificationType> getEnabledTypes() {
        return enabledTypes;
    }

    public void setEnabledTypes(Set<Notification.NotificationType> enabledTypes) {
        this.enabledTypes = enabledTypes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
