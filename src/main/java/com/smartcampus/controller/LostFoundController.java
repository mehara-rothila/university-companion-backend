package com.smartcampus.controller;

import com.smartcampus.dto.LostFoundItemRequest;
import com.smartcampus.dto.LostFoundItemResponse;
import com.smartcampus.model.LostFoundItem;
import com.smartcampus.model.User;
import com.smartcampus.repository.LostFoundItemRepository;
import com.smartcampus.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/lost-found")
public class LostFoundController {
    
    @Autowired
    private LostFoundItemRepository lostFoundItemRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping("/items")
    public ResponseEntity<List<LostFoundItemResponse>> getAllItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        
        LostFoundItem.ItemType itemType = null;
        if (type != null && !type.equals("all")) {
            try {
                itemType = LostFoundItem.ItemType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        
        LostFoundItem.ItemStatus itemStatus;
        try {
            itemStatus = LostFoundItem.ItemStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            itemStatus = LostFoundItem.ItemStatus.ACTIVE;
        }
        
        String categoryFilter = (category != null && !category.equals("all")) ? category : null;
        String locationFilter = (location != null && !location.equals("all")) ? location : null;
        String searchFilter = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        
        List<LostFoundItem> items = lostFoundItemRepository.findItemsWithFilters(
            itemType, categoryFilter, locationFilter, searchFilter, itemStatus);
        
        List<LostFoundItemResponse> response = items.stream()
            .map(LostFoundItemResponse::new)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/items/{id}")
    public ResponseEntity<LostFoundItemResponse> getItemById(@PathVariable Long id) {
        Optional<LostFoundItem> item = lostFoundItemRepository.findById(id);
        
        if (item.isPresent()) {
            return ResponseEntity.ok(new LostFoundItemResponse(item.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/items")
    public ResponseEntity<?> createItem(@Valid @RequestBody LostFoundItemRequest request) {
        // For now, we'll use a default user. In real implementation, get from authentication
        Optional<User> userOpt = userRepository.findById(1L);
        if (!userOpt.isPresent()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        
        User user = userOpt.get();
        
        LostFoundItem item = new LostFoundItem(
            request.getType(),
            request.getTitle(),
            request.getDescription(),
            request.getCategory(),
            request.getLocation(),
            user
        );
        
        item.setImageUrl(request.getImageUrl());
        item.setReward(request.getReward());
        item.setContactMethod(request.getContactMethod());
        item.setPriority(request.getPriority());
        
        if (request.getTags() != null) {
            item.setTags(request.getTags());
        }
        
        LostFoundItem savedItem = lostFoundItemRepository.save(item);
        
        return ResponseEntity.ok(new LostFoundItemResponse(savedItem));
    }
    
    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, 
                                       @Valid @RequestBody LostFoundItemRequest request) {
        Optional<LostFoundItem> itemOpt = lostFoundItemRepository.findById(id);
        
        if (!itemOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        LostFoundItem item = itemOpt.get();
        
        item.setType(request.getType());
        item.setTitle(request.getTitle());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setLocation(request.getLocation());
        item.setImageUrl(request.getImageUrl());
        item.setReward(request.getReward());
        item.setContactMethod(request.getContactMethod());
        item.setPriority(request.getPriority());
        
        if (request.getTags() != null) {
            item.setTags(request.getTags());
        }
        
        LostFoundItem savedItem = lostFoundItemRepository.save(item);
        
        return ResponseEntity.ok(new LostFoundItemResponse(savedItem));
    }
    
    @PutMapping("/items/{id}/status")
    public ResponseEntity<?> updateItemStatus(@PathVariable Long id, 
                                             @RequestParam String status) {
        Optional<LostFoundItem> itemOpt = lostFoundItemRepository.findById(id);
        
        if (!itemOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            LostFoundItem.ItemStatus newStatus = LostFoundItem.ItemStatus.valueOf(status.toUpperCase());
            LostFoundItem item = itemOpt.get();
            item.setStatus(newStatus);
            
            LostFoundItem savedItem = lostFoundItemRepository.save(item);
            return ResponseEntity.ok(new LostFoundItemResponse(savedItem));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status);
        }
    }
    
    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable Long id) {
        Optional<LostFoundItem> itemOpt = lostFoundItemRepository.findById(id);
        
        if (!itemOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        lostFoundItemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/items/user/{userId}")
    public ResponseEntity<List<LostFoundItemResponse>> getUserItems(@PathVariable Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        List<LostFoundItem> items = lostFoundItemRepository.findByPostedBy(userOpt.get());
        List<LostFoundItemResponse> response = items.stream()
            .map(LostFoundItemResponse::new)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Long totalItems = lostFoundItemRepository.count();
        Long lostItems = lostFoundItemRepository.countByTypeAndStatus(
            LostFoundItem.ItemType.LOST, LostFoundItem.ItemStatus.ACTIVE);
        Long foundItems = lostFoundItemRepository.countByTypeAndStatus(
            LostFoundItem.ItemType.FOUND, LostFoundItem.ItemStatus.ACTIVE);
        Long resolvedItems = lostFoundItemRepository.countByTypeAndStatus(
            LostFoundItem.ItemType.LOST, LostFoundItem.ItemStatus.RESOLVED) +
            lostFoundItemRepository.countByTypeAndStatus(
            LostFoundItem.ItemType.FOUND, LostFoundItem.ItemStatus.RESOLVED);
        
        stats.put("totalItems", totalItems);
        stats.put("lostItems", lostItems);
        stats.put("foundItems", foundItems);
        stats.put("resolvedItems", resolvedItems);
        
        // Get categories and locations for filters
        List<String> categories = lostFoundItemRepository.findDistinctCategories(LostFoundItem.ItemStatus.ACTIVE);
        List<String> locations = lostFoundItemRepository.findDistinctLocations(LostFoundItem.ItemStatus.ACTIVE);
        
        stats.put("categories", categories);
        stats.put("locations", locations);
        
        return ResponseEntity.ok(stats);
    }
}