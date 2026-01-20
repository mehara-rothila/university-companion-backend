package com.smartuniversity.dto;

import com.smartuniversity.model.LostFoundConversation;
import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.model.User;

import java.time.LocalDateTime;

public class LostFoundConversationResponse {

    private Long id;
    private ItemSummary item;
    private UserSummary requester;
    private UserSummary owner;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private String initialMessage;
    private MessageSummary lastMessage;
    private Long unreadCount;

    public LostFoundConversationResponse() {}

    public LostFoundConversationResponse(LostFoundConversation conversation) {
        this.id = conversation.getId();
        this.status = conversation.getStatus().toString();
        this.createdAt = conversation.getCreatedAt();
        this.updatedAt = conversation.getUpdatedAt();
        this.approvedAt = conversation.getApprovedAt();
        this.initialMessage = conversation.getInitialMessage();

        if (conversation.getItem() != null) {
            this.item = new ItemSummary(conversation.getItem());
        }

        if (conversation.getRequester() != null) {
            this.requester = new UserSummary(conversation.getRequester());
        }

        if (conversation.getOwner() != null) {
            this.owner = new UserSummary(conversation.getOwner());
        }
    }

    // Inner classes for nested data
    public static class ItemSummary {
        private Long id;
        private String title;
        private String type;
        private String imageUrl;
        private String category;
        private String location;
        private String contactMethod;

        public ItemSummary() {}

        public ItemSummary(LostFoundItem item) {
            this.id = item.getId();
            this.title = item.getTitle();
            this.type = item.getType() != null ? item.getType().toString() : null;
            this.imageUrl = item.getImageUrl();
            this.category = item.getCategory();
            this.location = item.getLocation();
            this.contactMethod = item.getContactMethod() != null ? item.getContactMethod().toString() : null;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public String getContactMethod() { return contactMethod; }
        public void setContactMethod(String contactMethod) { this.contactMethod = contactMethod; }
    }

    public static class UserSummary {
        private Long id;
        private String username;
        private String fullName;
        private String profileImage;

        public UserSummary() {}

        public UserSummary(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            this.fullName = (firstName + " " + lastName).trim();
            this.profileImage = user.getImageUrl();
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

    public static class MessageSummary {
        private String content;
        private LocalDateTime sentAt;
        private Long senderId;
        private boolean isRead;

        public MessageSummary() {}

        public MessageSummary(String content, LocalDateTime sentAt, Long senderId, boolean isRead) {
            this.content = content;
            this.sentAt = sentAt;
            this.senderId = senderId;
            this.isRead = isRead;
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public LocalDateTime getSentAt() { return sentAt; }
        public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }

        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ItemSummary getItem() { return item; }
    public void setItem(ItemSummary item) { this.item = item; }

    public UserSummary getRequester() { return requester; }
    public void setRequester(UserSummary requester) { this.requester = requester; }

    public UserSummary getOwner() { return owner; }
    public void setOwner(UserSummary owner) { this.owner = owner; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getInitialMessage() { return initialMessage; }
    public void setInitialMessage(String initialMessage) { this.initialMessage = initialMessage; }

    public MessageSummary getLastMessage() { return lastMessage; }
    public void setLastMessage(MessageSummary lastMessage) { this.lastMessage = lastMessage; }

    public Long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
}
