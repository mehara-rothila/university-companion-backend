package com.smartuniversity.dto;

public class AttendanceRequest {
    private Long userId;
    private String notes;

    public AttendanceRequest() {
    }

    public AttendanceRequest(Long userId, String notes) {
        this.userId = userId;
        this.notes = notes;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
