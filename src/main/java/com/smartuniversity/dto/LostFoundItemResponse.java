package com.smartuniversity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartuniversity.model.LostFoundItem;

import java.time.LocalDateTime;
import java.util.Set;

public class LostFoundItemResponse {
    
    private Long id;
    private LostFoundItem.ItemType type;
    private String title;
    private String description;
    private String category;
    private String location;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime dateReported;
    
    private String imageUrl;
    private Double reward;
    private LostFoundItem.ContactMethod contactMethod;
    private LostFoundItem.ItemStatus status;
    private String postedBy;
    private Long postedByUserId;
    private LostFoundItem.Priority priority;
    private Set<String> tags;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime updatedAt;
    
    public LostFoundItemResponse() {}
    
    public LostFoundItemResponse(LostFoundItem item) {
        try {
            this.id = item.getId();
            this.type = item.getType();
            this.title = item.getTitle();
            this.description = item.getDescription();
            this.category = item.getCategory();
            this.location = item.getLocation();
            this.dateReported = item.getDateReported();
            this.imageUrl = item.getImageUrl();
            this.reward = item.getReward();
            this.contactMethod = item.getContactMethod() != null ? item.getContactMethod() : LostFoundItem.ContactMethod.DIRECT;
            this.status = item.getStatus() != null ? item.getStatus() : LostFoundItem.ItemStatus.ACTIVE;
            this.priority = item.getPriority() != null ? item.getPriority() : LostFoundItem.Priority.MEDIUM;
            this.tags = item.getTags();
            this.createdAt = item.getCreatedAt();
            this.updatedAt = item.getUpdatedAt();
            
            if (item.getPostedBy() != null) {
                try {
                    this.postedByUserId = item.getPostedBy().getId();
                    if (item.getContactMethod() == LostFoundItem.ContactMethod.DIRECT) {
                        String firstName = item.getPostedBy().getFirstName() != null ? item.getPostedBy().getFirstName() : "User";
                        String lastName = item.getPostedBy().getLastName();
                        String lastInitial = (lastName != null && !lastName.isEmpty()) ? lastName.charAt(0) + "." : "";
                        this.postedBy = firstName + " " + lastInitial;
                    } else {
                        this.postedBy = "Anonymous";
                    }
                } catch (Exception e) {
                    System.err.println("Error setting user information: " + e.getMessage());
                    this.postedBy = "Unknown";
                    this.postedByUserId = null;
                }
            } else {
                this.postedBy = "Unknown";
                this.postedByUserId = null;
            }
        } catch (Exception e) {
            System.err.println("Error creating LostFoundItemResponse: " + e.getMessage());
            e.printStackTrace();
            // Set default values for critical fields
            if (this.contactMethod == null) this.contactMethod = LostFoundItem.ContactMethod.DIRECT;
            if (this.status == null) this.status = LostFoundItem.ItemStatus.ACTIVE;
            if (this.priority == null) this.priority = LostFoundItem.Priority.MEDIUM;
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public LostFoundItem.ItemType getType() { return type; }
    public void setType(LostFoundItem.ItemType type) { this.type = type; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public LocalDateTime getDateReported() { return dateReported; }
    public void setDateReported(LocalDateTime dateReported) { this.dateReported = dateReported; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public Double getReward() { return reward; }
    public void setReward(Double reward) { this.reward = reward; }
    
    public LostFoundItem.ContactMethod getContactMethod() { return contactMethod; }
    public void setContactMethod(LostFoundItem.ContactMethod contactMethod) { this.contactMethod = contactMethod; }
    
    public LostFoundItem.ItemStatus getStatus() { return status; }
    public void setStatus(LostFoundItem.ItemStatus status) { this.status = status; }
    
    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }
    
    public Long getPostedByUserId() { return postedByUserId; }
    public void setPostedByUserId(Long postedByUserId) { this.postedByUserId = postedByUserId; }
    
    public LostFoundItem.Priority getPriority() { return priority; }
    public void setPriority(LostFoundItem.Priority priority) { this.priority = priority; }
    
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}