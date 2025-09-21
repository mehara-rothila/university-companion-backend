package com.smartcampus.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "lost_found_items")
public class LostFoundItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private ItemType type;
    
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
    
    private LocalDateTime dateReported = LocalDateTime.now();
    
    @Size(max = 500)
    private String imageUrl;
    
    private Double reward;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private ContactMethod contactMethod = ContactMethod.DIRECT;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    private ItemStatus status = ItemStatus.ACTIVE;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User postedBy;
    
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;
    
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> tags = new HashSet<>();
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum ItemType {
        LOST, FOUND
    }
    
    public enum ContactMethod {
        ANONYMOUS, DIRECT
    }
    
    public enum ItemStatus {
        ACTIVE, RESOLVED, EXPIRED
    }
    
    public enum Priority {
        HIGH, MEDIUM, LOW
    }
    
    public LostFoundItem() {}
    
    public LostFoundItem(ItemType type, String title, String description, String category, 
                        String location, User postedBy) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.category = category;
        this.location = location;
        this.postedBy = postedBy;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }
    
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
    
    public ContactMethod getContactMethod() { return contactMethod; }
    public void setContactMethod(ContactMethod contactMethod) { this.contactMethod = contactMethod; }
    
    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }
    
    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User postedBy) { this.postedBy = postedBy; }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}