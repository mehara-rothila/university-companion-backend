package com.smartuniversity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LostFoundConversationRequest {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @Size(max = 500, message = "Initial message cannot exceed 500 characters")
    private String initialMessage;

    public LostFoundConversationRequest() {}

    public LostFoundConversationRequest(Long itemId, String initialMessage) {
        this.itemId = itemId;
        this.initialMessage = initialMessage;
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public String getInitialMessage() { return initialMessage; }
    public void setInitialMessage(String initialMessage) { this.initialMessage = initialMessage; }
}
