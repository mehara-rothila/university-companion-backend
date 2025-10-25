package com.smartuniversity.dto;

public class WeatherChatRequest {
    private String message;
    private Long userId;

    // Constructors
    public WeatherChatRequest() {}

    public WeatherChatRequest(String message, Long userId) {
        this.message = message;
        this.userId = userId;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
