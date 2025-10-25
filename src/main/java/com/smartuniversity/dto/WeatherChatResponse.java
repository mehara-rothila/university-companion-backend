package com.smartuniversity.dto;

public class WeatherChatResponse {
    private String response;
    private boolean success;
    private String error;

    // Constructors
    public WeatherChatResponse() {}

    public WeatherChatResponse(String response, boolean success) {
        this.response = response;
        this.success = success;
    }

    public WeatherChatResponse(String response, boolean success, String error) {
        this.response = response;
        this.success = success;
        this.error = error;
    }

    // Static factory methods
    public static WeatherChatResponse success(String response) {
        return new WeatherChatResponse(response, true);
    }

    public static WeatherChatResponse error(String errorMessage) {
        return new WeatherChatResponse(null, false, errorMessage);
    }

    // Getters and Setters
    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
