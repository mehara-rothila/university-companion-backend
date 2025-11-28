package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_notification_acknowledgments")
public class EmergencyNotificationAcknowledgment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "seen_at")
    private LocalDateTime seenAt;

    @Column(name = "has_seen")
    private Boolean hasSeen = false;

    public EmergencyNotificationAcknowledgment() {}

    public EmergencyNotificationAcknowledgment(Notification notification, User user) {
        this.notification = notification;
        this.user = user;
        // Don't set acknowledgedAt or hasSeen automatically
        // These should only be set when the user actually interacts with the notification
        this.hasSeen = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public LocalDateTime getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(LocalDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public LocalDateTime getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(LocalDateTime seenAt) {
        this.seenAt = seenAt;
        this.hasSeen = (seenAt != null);
    }

    public Boolean getHasSeen() {
        return hasSeen;
    }

    public void setHasSeen(Boolean hasSeen) {
        this.hasSeen = hasSeen;
    }
}
