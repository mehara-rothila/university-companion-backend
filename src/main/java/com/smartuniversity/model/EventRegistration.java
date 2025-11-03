package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_registrations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime movedToWaitlistAt;

    private LocalDateTime movedFromWaitlistAt;

    public enum RegistrationStatus {
        REGISTERED, WAITLISTED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
    }

    // Constructors
    public EventRegistration() {}

    public EventRegistration(Long eventId, Long userId, RegistrationStatus status) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getMovedToWaitlistAt() {
        return movedToWaitlistAt;
    }

    public void setMovedToWaitlistAt(LocalDateTime movedToWaitlistAt) {
        this.movedToWaitlistAt = movedToWaitlistAt;
    }

    public LocalDateTime getMovedFromWaitlistAt() {
        return movedFromWaitlistAt;
    }

    public void setMovedFromWaitlistAt(LocalDateTime movedFromWaitlistAt) {
        this.movedFromWaitlistAt = movedFromWaitlistAt;
    }
}
