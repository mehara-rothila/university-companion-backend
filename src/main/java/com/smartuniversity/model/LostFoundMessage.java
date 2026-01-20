package com.smartuniversity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "lost_found_messages")
public class LostFoundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private LostFoundConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @NotBlank
    @Size(max = 2000)
    @Column(length = 2000, columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime sentAt = LocalDateTime.now();

    private LocalDateTime readAt;

    private boolean isRead = false;

    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.TEXT;

    public enum MessageType {
        TEXT,
        SYSTEM  // For system messages like "Conversation approved"
    }

    public LostFoundMessage() {}

    public LostFoundMessage(LostFoundConversation conversation, User sender, String content) {
        this.conversation = conversation;
        this.sender = sender;
        this.content = content;
    }

    public static LostFoundMessage createSystemMessage(LostFoundConversation conversation, User sender, String content) {
        LostFoundMessage message = new LostFoundMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setMessageType(MessageType.SYSTEM);
        return message;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LostFoundConversation getConversation() { return conversation; }
    public void setConversation(LostFoundConversation conversation) { this.conversation = conversation; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
