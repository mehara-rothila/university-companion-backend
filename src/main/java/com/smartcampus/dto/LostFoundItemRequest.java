package com.smartcampus.dto;

import com.smartcampus.model.LostFoundItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class LostFoundItemRequest {
    
    @NotNull
    private LostFoundItem.ItemType type;
    
    @NotBlank
    @Size(max = 200)
    private String title;
    
    @NotBlank
    @Size(max = 1000)
    private String description;
    
    @NotBlank
    @Size(max = 100)
    private String category;
    
    @NotBlank
    @Size(max = 200)
    private String location;
    
    @Size(max = 500)
    private String imageUrl;
    
    private Double reward;
    
    @NotNull
    private LostFoundItem.ContactMethod contactMethod = LostFoundItem.ContactMethod.DIRECT;
    
    private LostFoundItem.Priority priority = LostFoundItem.Priority.MEDIUM;
    
    private Set<String> tags;
    
    public LostFoundItemRequest() {}
    
    // Getters and Setters
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
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public Double getReward() { return reward; }
    public void setReward(Double reward) { this.reward = reward; }
    
    public LostFoundItem.ContactMethod getContactMethod() { return contactMethod; }
    public void setContactMethod(LostFoundItem.ContactMethod contactMethod) { this.contactMethod = contactMethod; }
    
    public LostFoundItem.Priority getPriority() { return priority; }
    public void setPriority(LostFoundItem.Priority priority) { this.priority = priority; }
    
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
}