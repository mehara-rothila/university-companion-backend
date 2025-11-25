package com.smartuniversity.controller;

import com.smartuniversity.dto.LostFoundItemRequest;
import com.smartuniversity.dto.LostFoundItemResponse;
import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.LostFoundItemRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.util.AuthUtils;
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

    @Autowired
    private AuthUtils authUtils;

    // DEVELOPMENT ONLY: Clear all lost and found items
    @DeleteMapping("/items/clear-all")
    public ResponseEntity<?> clearAllItems() {
        try {
            long count = lostFoundItemRepository.count();
            lostFoundItemRepository.deleteAll();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "All lost and found items cleared successfully");
            response.put("itemsDeleted", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error clearing items: " + e.getMessage());
        }
    }

    @GetMapping("/items")
    public ResponseEntity<List<LostFoundItemResponse>> getAllItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ACTIVE") String status) {

        try {
            // Fetch all items with user data to avoid LazyInitializationException
            List<LostFoundItem> items;
            try {
                items = lostFoundItemRepository.findAllWithUsers();
            } catch (Exception dbError) {
                System.err.println("Database query failed: " + dbError.getMessage());
                dbError.printStackTrace();
                // Return empty list if database fails
                return ResponseEntity.ok(List.of());
            }

            // Apply filters with null-safe operations
            items = items.stream()
                    .filter(item -> type == null
                            || (item.getType() != null && item.getType().toString().equalsIgnoreCase(type)))
                    .filter(item -> category == null
                            || (item.getCategory() != null && item.getCategory().equalsIgnoreCase(category)))
                    .filter(item -> location == null
                            || (item.getLocation() != null && item.getLocation().equalsIgnoreCase(location)))
                    .filter(item -> status == null
                            || (item.getStatus() != null && item.getStatus().toString().equalsIgnoreCase(status)))
                    .filter(item -> search == null ||
                            (item.getTitle() != null && item.getTitle().toLowerCase().contains(search.toLowerCase())) ||
                            (item.getDescription() != null
                                    && item.getDescription().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());

            List<LostFoundItemResponse> response = items.stream()
                    .map(item -> {
                        try {
                            return new LostFoundItemResponse(item);
                        } catch (Exception e) {
                            System.err.println(
                                    "Error creating response for item " + item.getId() + ": " + e.getMessage());
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in getAllItems endpoint: " + e.getMessage());
            e.printStackTrace();
            // Return empty list instead of 500 error
            return ResponseEntity.ok(List.of());
        }
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
    public ResponseEntity<?> createItem(
            @Valid @RequestBody LostFoundItemRequest request,
            org.springframework.validation.BindingResult bindingResult,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (bindingResult.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            bindingResult.getAllErrors().forEach(error -> {
                if (error instanceof org.springframework.validation.FieldError) {
                    sb.append(((org.springframework.validation.FieldError) error).getField()).append(": ");
                }
                sb.append(error.getDefaultMessage()).append("; ");
            });
            System.out.println("Validation errors: " + sb.toString());
            return ResponseEntity.badRequest().body("Validation error: " + sb.toString());
        }

        // Get user from authentication (handles JWT extraction and fallbacks)
        User user = authUtils.getUserFromAuthHeader(authHeader);

        LostFoundItem item = new LostFoundItem(
                request.getType(),
                request.getTitle(),
                request.getDescription(),
                request.getCategory(),
                request.getLocation(),
                user);

        item.setImageUrl(request.getImageUrl());
        item.setReward(request.getReward());
        item.setContactMethod(request.getContactMethod());
        item.setPriority(request.getPriority());
        item.setStatus(LostFoundItem.ItemStatus.PENDING); // Require admin approval

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
        try {
            Optional<User> userOpt = userRepository.findById(userId);

            if (!userOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            List<LostFoundItem> items = lostFoundItemRepository.findByPostedBy(userOpt.get());
            List<LostFoundItemResponse> response = items.stream()
                    .map(item -> {
                        try {
                            return new LostFoundItemResponse(item);
                        } catch (Exception e) {
                            System.err.println(
                                    "Error creating response for item " + item.getId() + ": " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching user items for userId " + userId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
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
        } catch (Exception e) {
            System.err.println("Error fetching lost-found stats: " + e.getMessage());
            e.printStackTrace();

            // Return default values if database issues
            stats.put("totalItems", 0L);
            stats.put("lostItems", 0L);
            stats.put("foundItems", 0L);
            stats.put("resolvedItems", 0L);
            stats.put("categories", List.of("Electronics", "Keys", "Personal Items", "Documents", "Other"));
            stats.put("locations", List.of("Library", "Cafeteria", "Parking Lot", "Computer Lab", "Gym"));
        }

        return ResponseEntity.ok(stats);
    }

    // Admin endpoint to get pending items for review
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingItems(@RequestParam Long adminId) {
        try {
            // Verify admin role
            Optional<User> adminOpt = userRepository.findById(adminId);
            if (!adminOpt.isPresent()) {
                return ResponseEntity.status(404).body("Admin user not found");
            }

            User admin = adminOpt.get();
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
            }

            // Get all pending items with user data
            List<LostFoundItem> pendingItems = lostFoundItemRepository.findAllWithUsers().stream()
                    .filter(item -> item.getStatus() == LostFoundItem.ItemStatus.PENDING)
                    .collect(Collectors.toList());

            List<LostFoundItemResponse> response = pendingItems.stream()
                    .map(item -> {
                        try {
                            return new LostFoundItemResponse(item);
                        } catch (Exception e) {
                            System.err.println("Error creating response for item " + item.getId() + ": " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching pending items: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error fetching pending items: " + e.getMessage());
        }
    }

    // Admin endpoint to approve an item
    @PostMapping("/{itemId}/approve")
    public ResponseEntity<?> approveItem(@PathVariable Long itemId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            Optional<User> adminOpt = userRepository.findById(adminId);
            if (!adminOpt.isPresent()) {
                return ResponseEntity.status(404).body("Admin user not found");
            }

            User admin = adminOpt.get();
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
            }

            // Get the item
            Optional<LostFoundItem> itemOpt = lostFoundItemRepository.findById(itemId);
            if (!itemOpt.isPresent()) {
                return ResponseEntity.status(404).body("Item not found");
            }

            LostFoundItem item = itemOpt.get();

            // Only pending items can be approved
            if (item.getStatus() != LostFoundItem.ItemStatus.PENDING) {
                return ResponseEntity.badRequest().body("Only pending items can be approved");
            }

            // Approve the item
            item.setStatus(LostFoundItem.ItemStatus.ACTIVE);
            LostFoundItem savedItem = lostFoundItemRepository.save(item);

            return ResponseEntity.ok(new LostFoundItemResponse(savedItem));
        } catch (Exception e) {
            System.err.println("Error approving item: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error approving item: " + e.getMessage());
        }
    }

    // Admin endpoint to reject an item
    @PostMapping("/{itemId}/reject")
    public ResponseEntity<?> rejectItem(@PathVariable Long itemId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            Optional<User> adminOpt = userRepository.findById(adminId);
            if (!adminOpt.isPresent()) {
                return ResponseEntity.status(404).body("Admin user not found");
            }

            User admin = adminOpt.get();
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
            }

            // Get the item
            Optional<LostFoundItem> itemOpt = lostFoundItemRepository.findById(itemId);
            if (!itemOpt.isPresent()) {
                return ResponseEntity.status(404).body("Item not found");
            }

            LostFoundItem item = itemOpt.get();

            // Only pending items can be rejected
            if (item.getStatus() != LostFoundItem.ItemStatus.PENDING) {
                return ResponseEntity.badRequest().body("Only pending items can be rejected");
            }

            // Reject the item
            item.setStatus(LostFoundItem.ItemStatus.REJECTED);
            LostFoundItem savedItem = lostFoundItemRepository.save(item);

            return ResponseEntity.ok(new LostFoundItemResponse(savedItem));
        } catch (Exception e) {
            System.err.println("Error rejecting item: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error rejecting item: " + e.getMessage());
        }
    }
}