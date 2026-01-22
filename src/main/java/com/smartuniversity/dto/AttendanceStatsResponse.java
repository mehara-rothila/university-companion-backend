package com.smartuniversity.dto;

public class AttendanceStatsResponse {
    private Long eventId;
    private String eventTitle;
    private long totalRegistered;
    private long totalAttended;
    private long totalWaitlisted;
    private double attendanceRate;

    public AttendanceStatsResponse() {
    }

    public AttendanceStatsResponse(Long eventId, String eventTitle, long totalRegistered,
                                 long totalAttended, long totalWaitlisted, double attendanceRate) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.totalRegistered = totalRegistered;
        this.totalAttended = totalAttended;
        this.totalWaitlisted = totalWaitlisted;
        this.attendanceRate = attendanceRate;
    }

    // Getters and Setters
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

    public long getTotalRegistered() {
        return totalRegistered;
    }

    public void setTotalRegistered(long totalRegistered) {
        this.totalRegistered = totalRegistered;
    }

    public long getTotalAttended() {
        return totalAttended;
    }

    public void setTotalAttended(long totalAttended) {
        this.totalAttended = totalAttended;
    }

    public long getTotalWaitlisted() {
        return totalWaitlisted;
    }

    public void setTotalWaitlisted(long totalWaitlisted) {
        this.totalWaitlisted = totalWaitlisted;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
