package com.smartuniversity.dto;

import java.time.LocalDateTime;

public class AttendanceResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private Long userId;
    private String userName;
    private String userEmail;
    private LocalDateTime checkedInAt;
    private Long checkedInBy;
    private String checkedInByName;
    private String notes;

    public AttendanceResponse() {
    }

    public AttendanceResponse(Long id, Long eventId, String eventTitle, Long userId, String userName,
                            String userEmail, LocalDateTime checkedInAt, Long checkedInBy,
                            String checkedInByName, String notes) {
        this.id = id;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.checkedInAt = checkedInAt;
        this.checkedInBy = checkedInBy;
        this.checkedInByName = checkedInByName;
        this.notes = notes;
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

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public Long getCheckedInBy() {
        return checkedInBy;
    }

    public void setCheckedInBy(Long checkedInBy) {
        this.checkedInBy = checkedInBy;
    }

    public String getCheckedInByName() {
        return checkedInByName;
    }

    public void setCheckedInByName(String checkedInByName) {
        this.checkedInByName = checkedInByName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
