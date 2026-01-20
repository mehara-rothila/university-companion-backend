package com.smartuniversity.dto;

import jakarta.validation.constraints.NotNull;

public class BlockUserRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String reason;

    public BlockUserRequest() {}

    public BlockUserRequest(Long userId, String reason) {
        this.userId = userId;
        this.reason = reason;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
