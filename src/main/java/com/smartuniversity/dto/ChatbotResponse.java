package com.smartuniversity.dto;

public class ChatbotResponse {

    private boolean success;
    private String response;
    private String error;
    private Integer tokensUsed;
    private String intent;

    // Constructors
    public ChatbotResponse() {}

    public ChatbotResponse(boolean success, String response, String error) {
        this.success = success;
        this.response = response;
        this.error = error;
    }

    // Factory methods
    public static ChatbotResponse success(String response) {
        return new ChatbotResponse(true, response, null);
    }

    public static ChatbotResponse error(String error) {
        return new ChatbotResponse(false, null, error);
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }
}
