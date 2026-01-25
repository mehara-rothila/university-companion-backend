package com.smartuniversity.controller;

import com.smartuniversity.dto.EmergencyNotificationResponse;
import com.smartuniversity.dto.NotificationRequest;
import com.smartuniversity.model.EmergencyNotificationAcknowledgment;
import com.smartuniversity.model.Notification;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.EmergencyNotificationAcknowledgmentRepository;
import com.smartuniversity.repository.NotificationRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "http://localhost:3000")
public class EmergencyNotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmergencyNotificationAcknowledgmentRepository acknowledgmentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/create")
    public ResponseEntity<?> createEmergency(@Valid @RequestBody NotificationRequest request,
                                            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            System.out.println("🚨 [EMERGENCY CREATE] Request received: " + request.getTitle());

            // Verify admin user
            Optional<User> adminUser = getAdminFromToken(token);
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin user not found"));
            }

            // Force type to EMERGENCY
            Notification notification = new Notification();
            notification.setTitle(request.getTitle());
            notification.setMessage(request.getMessage());
            notification.setType(Notification.NotificationType.EMERGENCY);
            notification.setPriority(Notification.NotificationPriority.URGENT);
            notification.setTarget(request.getTarget());
            notification.setTargetUserIds(request.getTargetUserIds());
            notification.setExpiresAt(request.getExpiresAt());
            notification.setCreatedBy(adminUser.get());

            System.out.println("💾 [EMERGENCY CREATE] Saving notification to database...");
            Notification savedNotification = notificationRepository.save(notification);
            System.out.println("✅ [EMERGENCY CREATE] Saved with ID: " + savedNotification.getId());

            // Create acknowledgment records for all target users
            createAcknowledgmentRecords(savedNotification);

            // Send real-time WebSocket notification
            sendEmergencyBroadcast(savedNotification);

            return ResponseEntity.ok(buildEmergencyResponse(savedNotification, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create emergency: " + e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<EmergencyNotificationResponse>> getActiveEmergencies(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // If no token provided, return all active emergencies without user-specific data
            if (token == null || token.isEmpty()) {
                List<Notification> emergencies = notificationRepository.findActiveEmergencies(LocalDateTime.now());
                List<EmergencyNotificationResponse> responses = emergencies.stream()
                        .map(emergency -> buildEmergencyResponse(emergency, null))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
            }

            Optional<User> user = getUserFromToken(token);
            if (user.isEmpty()) {
                // Token invalid, still return public emergencies
                List<Notification> emergencies = notificationRepository.findActiveEmergencies(LocalDateTime.now());
                List<EmergencyNotificationResponse> responses = emergencies.stream()
                        .map(emergency -> buildEmergencyResponse(emergency, null))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
            }

            List<Notification> emergencies = notificationRepository.findEmergenciesForUser(user.get().getId(), LocalDateTime.now());

            List<EmergencyNotificationResponse> responses = emergencies.stream()
                    .map(emergency -> buildEmergencyResponse(emergency, user.get().getId()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of()); // Return empty list on error instead of 400
        }
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<EmergencyNotificationResponse>> getAllEmergencies(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Optional<User> adminUser = getAdminFromToken(token);
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().body(List.of());
            }

            List<Notification> emergencies = notificationRepository.findActiveEmergencies(LocalDateTime.now());
            System.out.println("📋 [ADMIN ALL] Found " + emergencies.size() + " emergencies in database");
            System.out.println("📋 [ADMIN ALL] Emergency IDs: " + emergencies.stream().map(Notification::getId).collect(Collectors.toList()));

            List<EmergencyNotificationResponse> responses = emergencies.stream()
                    .map(emergency -> buildEmergencyResponse(emergency, adminUser.get().getId()))
                    .collect(Collectors.toList());

            System.out.println("📤 [ADMIN ALL] Returning " + responses.size() + " responses");
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/seen")
    public ResponseEntity<?> markEmergencyAsSeen(@PathVariable Long id,
                                                 @RequestHeader("Authorization") String token) {
        try {
            Optional<User> user = getUserFromToken(token);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Optional<Notification> notification = notificationRepository.findById(id);
            if (notification.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Optional<EmergencyNotificationAcknowledgment> ack = acknowledgmentRepository
                    .findByNotificationIdAndUserId(id, user.get().getId());

            if (ack.isPresent()) {
                EmergencyNotificationAcknowledgment acknowledgment = ack.get();
                // Only update if not already seen
                if (acknowledgment.getSeenAt() == null) {
                    acknowledgment.setSeenAt(LocalDateTime.now());
                    acknowledgmentRepository.save(acknowledgment);
                }
            } else {
                // Create new acknowledgment record with seen timestamp
                EmergencyNotificationAcknowledgment newAck = new EmergencyNotificationAcknowledgment(
                        notification.get(), user.get());
                newAck.setSeenAt(LocalDateTime.now());
                acknowledgmentRepository.save(newAck);
            }

            return ResponseEntity.ok(Map.of("message", "Emergency marked as seen"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to mark as seen"));
        }
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<?> acknowledgeEmergency(@PathVariable Long id,
                                                 @RequestHeader("Authorization") String token) {
        try {
            Optional<User> user = getUserFromToken(token);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Optional<Notification> notification = notificationRepository.findById(id);
            if (notification.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Optional<EmergencyNotificationAcknowledgment> ack = acknowledgmentRepository
                    .findByNotificationIdAndUserId(id, user.get().getId());

            if (ack.isPresent()) {
                EmergencyNotificationAcknowledgment acknowledgment = ack.get();
                acknowledgment.setAcknowledgedAt(LocalDateTime.now());
                // Mark as seen when acknowledged (if not already seen)
                if (acknowledgment.getSeenAt() == null) {
                    acknowledgment.setSeenAt(LocalDateTime.now());
                }
                acknowledgmentRepository.save(acknowledgment);
            } else {
                EmergencyNotificationAcknowledgment newAck = new EmergencyNotificationAcknowledgment(
                        notification.get(), user.get());
                newAck.setAcknowledgedAt(LocalDateTime.now());
                newAck.setSeenAt(LocalDateTime.now());
                acknowledgmentRepository.save(newAck);
            }

            return ResponseEntity.ok(Map.of("message", "Emergency acknowledged"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to acknowledge"));
        }
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<?> dismissEmergency(@PathVariable Long id,
                                             @RequestHeader("Authorization") String token) {
        try {
            Optional<User> user = getUserFromToken(token);
            if (user.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Optional<Notification> notification = notificationRepository.findById(id);
            if (notification.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Optional<EmergencyNotificationAcknowledgment> ack = acknowledgmentRepository
                    .findByNotificationIdAndUserId(id, user.get().getId());

            if (ack.isPresent()) {
                EmergencyNotificationAcknowledgment acknowledgment = ack.get();
                acknowledgment.setDismissedAt(LocalDateTime.now());
                // Mark as seen when dismissed (if not already seen)
                if (acknowledgment.getSeenAt() == null) {
                    acknowledgment.setSeenAt(LocalDateTime.now());
                }
                acknowledgmentRepository.save(acknowledgment);
            } else {
                EmergencyNotificationAcknowledgment newAck = new EmergencyNotificationAcknowledgment(
                        notification.get(), user.get());
                newAck.setDismissedAt(LocalDateTime.now());
                newAck.setSeenAt(LocalDateTime.now());
                acknowledgmentRepository.save(newAck);
            }

            return ResponseEntity.ok(Map.of("message", "Emergency dismissed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to dismiss"));
        }
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getEmergencyStats(@PathVariable Long id,
                                              @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Optional<User> adminUser = getAdminFromToken(token);
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin access required"));
            }

            Optional<Notification> notification = notificationRepository.findById(id);
            if (notification.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            List<EmergencyNotificationAcknowledgment> allAcknowledgments = acknowledgmentRepository
                    .findByNotificationId(id);

            long seenCount = allAcknowledgments.stream()
                    .filter(a -> a.getHasSeen()).count();

            long dismissedCount = allAcknowledgments.stream()
                    .filter(a -> a.getDismissedAt() != null).count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTargeted", allAcknowledgments.size());
            stats.put("seen", seenCount);
            stats.put("dismissed", dismissedCount);
            stats.put("pending", allAcknowledgments.size() - seenCount);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get stats"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmergency(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Optional<User> adminUser = getAdminFromToken(token);
            if (adminUser.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Admin access required"));
            }

            if (!notificationRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            // Delete acknowledgments FIRST to avoid foreign key constraint violation
            acknowledgmentRepository.deleteAll(acknowledgmentRepository.findByNotificationId(id));

            // Then delete the notification
            notificationRepository.deleteById(id);

            return ResponseEntity.ok(Map.of("message", "Emergency deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete"));
        }
    }

    // Helper methods

    private void createAcknowledgmentRecords(Notification notification) {
        List<User> targetUsers = new ArrayList<>();

        if (notification.getTarget() == Notification.NotificationTarget.ALL_STUDENTS) {
            targetUsers = userRepository.findAll().stream()
                    .filter(user -> !user.getRole().equals(User.UserRole.ADMIN))
                    .collect(Collectors.toList());
        } else if (notification.getTarget() == Notification.NotificationTarget.SPECIFIC_USERS &&
                notification.getTargetUserIds() != null) {
            targetUsers = userRepository.findAllById(notification.getTargetUserIds());
        }

        for (User user : targetUsers) {
            EmergencyNotificationAcknowledgment ack = new EmergencyNotificationAcknowledgment(notification, user);
            // hasSeen is already set to false in constructor
            acknowledgmentRepository.save(ack);
        }
    }

    private EmergencyNotificationResponse buildEmergencyResponse(Notification notification, Long userId) {
        List<EmergencyNotificationAcknowledgment> allAcks = acknowledgmentRepository.findByNotificationId(notification.getId());

        Long seenCount = allAcks.stream().filter(a -> a.getHasSeen()).count();
        Long dismissedCount = allAcks.stream().filter(a -> a.getDismissedAt() != null).count();

        Boolean userDismissed = false;
        Boolean userSeen = false;

        if (userId != null) {
            Optional<EmergencyNotificationAcknowledgment> userAck = allAcks.stream()
                    .filter(a -> a.getUser().getId().equals(userId))
                    .findFirst();

            if (userAck.isPresent()) {
                userDismissed = userAck.get().getDismissedAt() != null;
                userSeen = userAck.get().getHasSeen();
            }
        }

        Long totalUsers = (long) allAcks.size();

        return new EmergencyNotificationResponse(notification, seenCount, dismissedCount, userDismissed, userSeen, totalUsers);
    }

    private void sendEmergencyBroadcast(Notification notification) {
        try {
            Map<String, Object> emergencyMessage = new HashMap<>();
            emergencyMessage.put("id", notification.getId());
            emergencyMessage.put("title", notification.getTitle());
            emergencyMessage.put("message", notification.getMessage());
            emergencyMessage.put("type", "EMERGENCY");
            emergencyMessage.put("priority", notification.getPriority().toString());
            emergencyMessage.put("timestamp", notification.getCreatedAt());
            emergencyMessage.put("expiresAt", notification.getExpiresAt());

            if (notification.getTarget() == Notification.NotificationTarget.ALL_STUDENTS) {
                messagingTemplate.convertAndSend("/topic/emergency", emergencyMessage);
            } else if (notification.getTarget() == Notification.NotificationTarget.SPECIFIC_USERS &&
                    notification.getTargetUserIds() != null) {
                for (Long userId : notification.getTargetUserIds()) {
                    messagingTemplate.convertAndSend("/topic/emergency/" + userId, emergencyMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Optional<User> getAdminFromToken(String token) {
        try {
            if (token != null && !token.isEmpty() && !token.equals("Bearer ")) {
                try {
                    String jwt = token.substring(7);
                    String username = jwtUtils.getUserNameFromJwtToken(jwt);
                    Optional<User> user = userRepository.findByUsername(username);
                    if (user.isPresent() && user.get().getRole().equals(User.UserRole.ADMIN)) {
                        return user;
                    }
                } catch (Exception e) {
                    // Fall back to first admin
                }
            }

            return userRepository.findAll().stream()
                    .filter(user -> user.getRole().equals(User.UserRole.ADMIN))
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<User> getUserFromToken(String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                String jwt = token.substring(7);
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                return userRepository.findByUsername(username);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
