package com.smartuniversity.dto;

import com.smartuniversity.model.LostFoundMessage;

import java.time.LocalDateTime;

public class LostFoundMessageResponse {

    private Long id;
    private Long conversationId;
    private SenderInfo sender;
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private boolean isRead;
    private String messageType;

    public LostFoundMessageResponse() {}

    public LostFoundMessageResponse(LostFoundMessage message) {
        this.id = message.getId();
        this.conversationId = message.getConversation().getId();
        this.content = message.getContent();
        this.sentAt = message.getSentAt();
        this.readAt = message.getReadAt();
        this.isRead = message.isRead();
        this.messageType = message.getMessageType().toString();

        if (message.getSender() != null) {
            this.sender = new SenderInfo(
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getSender().getFirstName() + " " + message.getSender().getLastName(),
                message.getSender().getImageUrl()
            );
        }
    }

    public static class SenderInfo {
        private Long id;
        private String username;
        private String fullName;
        private String profileImage;

        public SenderInfo() {}

        public SenderInfo(Long id, String username, String fullName, String profileImage) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.profileImage = profileImage;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getProfileImage() { return profileImage; }
        public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public SenderInfo getSender() { return sender; }
    public void setSender(SenderInfo sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
}
