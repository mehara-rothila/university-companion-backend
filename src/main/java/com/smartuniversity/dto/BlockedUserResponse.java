package com.smartuniversity.dto;

import com.smartuniversity.model.UserBlock;
import java.time.LocalDateTime;

public class BlockedUserResponse {

    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String profileImage;
    private LocalDateTime blockedAt;
    private String reason;

    public BlockedUserResponse() {}

    public BlockedUserResponse(UserBlock block) {
        this.id = block.getId();
        this.userId = block.getBlocked().getId();
        this.username = block.getBlocked().getUsername();
        String firstName = block.getBlocked().getFirstName() != null ? block.getBlocked().getFirstName() : "";
        String lastName = block.getBlocked().getLastName() != null ? block.getBlocked().getLastName() : "";
        this.fullName = (firstName + " " + lastName).trim();
        this.profileImage = block.getBlocked().getImageUrl();
        this.blockedAt = block.getCreatedAt();
        this.reason = block.getReason();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
