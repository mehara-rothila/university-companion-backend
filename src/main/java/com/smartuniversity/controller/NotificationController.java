package com.smartuniversity.controller;

import com.smartuniversity.dto.NotificationRequest;
import com.smartuniversity.dto.NotificationResponse;
import com.smartuniversity.model.Notification;
import com.smartuniversity.model.NotificationPreference;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.NotificationPreferenceRepository;
import com.smartuniversity.repository.NotificationRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/admin/create")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<?> createNotification(@Valid @RequestBody NotificationRequest request,
                                               @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // For testing purposes, use a default admin user if no token is provided
            Optional<User> adminUser;
            
            if (token != null && !token.isEmpty() && !token.equals("Bearer ")) {
                try {
                    String jwt = token.substring(7);
                    String username = jwtUtils.getUserNameFromJwtToken(jwt);
                    adminUser = userRepository.findByUsername(username);
                } catch (Exception e) {
                    // If JWT parsing fails, fall back to default admin user
                    adminUser = userRepository.findAll().stream()
                        .filter(user -> user.getRole().equals(User.UserRole.ADMIN))
                        .findFirst();
                }
            } else {
                // Fallback: use the first admin user found for testing
                adminUser = userRepository.findAll().stream()
                    .filter(user -> user.getRole().equals(User.UserRole.ADMIN))
                    .findFirst();
            }
            
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin user not found"));
            }

            Notification notification = new Notification();
            notification.setTitle(request.getTitle());
            notification.setMessage(request.getMessage());
            notification.setType(request.getType());
            notification.setPriority(request.getPriority());
            notification.setTarget(request.getTarget());
            notification.setTargetUserIds(request.getTargetUserIds());
            notification.setExpiresAt(request.getExpiresAt());
            notification.setCreatedBy(adminUser.get());

            Notification savedNotification = notificationRepository.save(notification);

            sendRealTimeNotification(savedNotification);

            return ResponseEntity.ok(new NotificationResponse(savedNotification));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create notification: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/all")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<Page<NotificationResponse>> getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationRepository.findActiveNotifications(LocalDateTime.now(), pageable);
        
        Page<NotificationResponse> response = notifications.map(NotificationResponse::new);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/my")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token) {
        
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);
            
            Optional<User> adminUser = userRepository.findByUsername(username);
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Notification> notifications = notificationRepository.findNotificationsByAdmin(adminUser.get().getId(), pageable);
            
            Page<NotificationResponse> response = notifications.map(NotificationResponse::new);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/student/my")
    public ResponseEntity<Page<NotificationResponse>> getStudentNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token) {
        
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);
            
            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Notification> notifications = notificationRepository.findNotificationsForUser(
                user.get().getId(), LocalDateTime.now(), pageable);
            
            Page<NotificationResponse> response = notifications.map(NotificationResponse::new);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/student/count")
    public ResponseEntity<Map<String, Long>> getNotificationCount(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);
            
            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Long count = notificationRepository.countActiveNotificationsForUser(user.get().getId(), LocalDateTime.now());
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/admin/{id}/toggle")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<?> toggleNotification(@PathVariable Long id) {
        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(id);
            if (notificationOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Notification notification = notificationOpt.get();
            notification.setIsActive(!notification.getIsActive());
            notificationRepository.save(notification);

            return ResponseEntity.ok(new NotificationResponse(notification));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to toggle notification"));
        }
    }

    @DeleteMapping("/admin/{id}")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        try {
            if (!notificationRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            notificationRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Notification deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete notification"));
        }
    }

    @GetMapping("/student/types")
    public ResponseEntity<List<String>> getNotificationTypes() {
        List<String> types = List.of(
            "GENERAL", "ACADEMIC", "FINANCIAL_AID", "LOST_FOUND",
            "WELLNESS", "DINING", "LIBRARY", "SOCIAL", "SYSTEM", "EMERGENCY"
        );
        return ResponseEntity.ok(types);
    }

    @GetMapping("/admin/users")
    // @PreAuthorize("hasRole('ADMIN')") // Commented out for testing
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            List<Map<String, Object>> userList = users.stream()
                .filter(user -> !user.getRole().equals(User.UserRole.ADMIN))
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("id", user.getId());
                    userMap.put("username", user.getUsername());
                    userMap.put("email", user.getEmail());
                    return userMap;
                })
                .collect(Collectors.toList());
            return ResponseEntity.ok(userList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private void sendRealTimeNotification(Notification notification) {
        try {
            NotificationMessage message = new NotificationMessage(
                notification.getTitle(),
                notification.getMessage(),
                notification.getType().toString(),
                notification.getPriority().toString(),
                notification.getId().toString(),
                LocalDateTime.now().toString()
            );

            if (notification.getTarget() == Notification.NotificationTarget.ALL_STUDENTS) {
                messagingTemplate.convertAndSend("/topic/notifications", message);
            } else if (notification.getTarget() == Notification.NotificationTarget.SPECIFIC_USERS 
                      && notification.getTargetUserIds() != null) {
                for (Long userId : notification.getTargetUserIds()) {
                    messagingTemplate.convertAndSend("/topic/notifications/" + userId, message);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String message) throws Exception {
        Thread.sleep(1000);
        return "Hello, " + message + "!";
    }

    // ============ Notification Preferences Management ============

    // Get user's notification preferences
    @GetMapping("/preferences")
    public ResponseEntity<?> getNotificationPreferences(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);

            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Long userId = user.get().getId();
            NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    // Create default preferences if none exist
                    NotificationPreference newPref = new NotificationPreference(userId);
                    return preferenceRepository.save(newPref);
                });

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("enabledTypes", preference.getEnabledTypes());
            response.put("note", "EMERGENCY notifications are always enabled and cannot be disabled");
            response.put("updatedAt", preference.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get preferences: " + e.getMessage()));
        }
    }

    // Update notification preferences (toggle a specific type)
    @PutMapping("/preferences/toggle")
    public ResponseEntity<?> toggleNotificationPreference(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);

            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            String typeStr = (String) request.get("type");
            Boolean enabled = (Boolean) request.get("enabled");

            if (typeStr == null || enabled == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "type and enabled fields are required"));
            }

            // Prevent disabling EMERGENCY notifications
            if (typeStr.equals("EMERGENCY")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Emergency notifications cannot be disabled for safety reasons"
                ));
            }

            Notification.NotificationType type;
            try {
                type = Notification.NotificationType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid notification type: " + typeStr));
            }

            Long userId = user.get().getId();
            NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference newPref = new NotificationPreference(userId);
                    return preferenceRepository.save(newPref);
                });

            preference.toggleType(type, enabled);
            preferenceRepository.save(preference);

            return ResponseEntity.ok(Map.of(
                "message", type + " notifications " + (enabled ? "enabled" : "disabled"),
                "type", type.toString(),
                "enabled", enabled,
                "enabledTypes", preference.getEnabledTypes()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update preference: " + e.getMessage()));
        }
    }

    // Bulk update notification preferences
    @PutMapping("/preferences")
    public ResponseEntity<?> updateNotificationPreferences(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {
        try {
            String jwt = token.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);

            Optional<User> user = userRepository.findByUsername(username);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            @SuppressWarnings("unchecked")
            List<String> enabledTypesList = (List<String>) request.get("enabledTypes");
            if (enabledTypesList == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "enabledTypes field is required"));
            }

            Long userId = user.get().getId();
            NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference newPref = new NotificationPreference(userId);
                    return preferenceRepository.save(newPref);
                });

            // Clear and set new preferences
            preference.getEnabledTypes().clear();
            for (String typeStr : enabledTypesList) {
                try {
                    Notification.NotificationType type = Notification.NotificationType.valueOf(typeStr.toUpperCase());
                    if (type != Notification.NotificationType.EMERGENCY) {
                        preference.getEnabledTypes().add(type);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid types
                }
            }

            preferenceRepository.save(preference);

            return ResponseEntity.ok(Map.of(
                "message", "Notification preferences updated",
                "enabledTypes", preference.getEnabledTypes(),
                "note", "EMERGENCY notifications are always enabled"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update preferences: " + e.getMessage()));
        }
    }

    public static class NotificationMessage {
        private String title;
        private String message;
        private String type;
        private String priority;
        private String id;
        private String timestamp;

        public NotificationMessage(String title, String message, String type, String priority, String id, String timestamp) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.priority = priority;
            this.id = id;
            this.timestamp = timestamp;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
}