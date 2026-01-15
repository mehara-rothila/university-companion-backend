package com.smartuniversity.controller;

import com.smartuniversity.model.Event;
import com.smartuniversity.model.Event.ApprovalStatus;
import com.smartuniversity.model.EventRegistration;
import com.smartuniversity.model.EventRegistration.RegistrationStatus;
import com.smartuniversity.model.EventComment;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.EventRepository;
import com.smartuniversity.repository.EventRegistrationRepository;
import com.smartuniversity.repository.EventCommentRepository;
import com.smartuniversity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventRegistrationRepository registrationRepository;

    @Autowired
    private EventCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    // Create a new event
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Map<String, Object> eventData) {
        try {
            Event event = new Event();
            event.setTitle((String) eventData.get("title"));
            event.setDescription((String) eventData.get("description"));
            event.setImageUrl((String) eventData.get("imageUrl"));
            event.setCategory((String) eventData.get("category"));
            event.setLocation((String) eventData.get("location"));
            event.setOrganizerName((String) eventData.get("organizerName"));
            event.setCreatorId(Long.valueOf(eventData.get("creatorId").toString()));

            // Parse ISO 8601 date strings
            event.setEventDate(LocalDateTime.parse(
                ((String) eventData.get("eventDate")).replace("Z", "")
            ));
            event.setEventTime(LocalDateTime.parse(
                ((String) eventData.get("eventTime")).replace("Z", "")
            ));
            event.setEndTime(LocalDateTime.parse(
                ((String) eventData.get("endTime")).replace("Z", "")
            ));

            if (eventData.containsKey("registrationDeadline") && eventData.get("registrationDeadline") != null) {
                event.setRegistrationDeadline(LocalDateTime.parse(
                    ((String) eventData.get("registrationDeadline")).replace("Z", "")
                ));
            }

            if (eventData.containsKey("maxAttendees") && eventData.get("maxAttendees") != null) {
                event.setMaxAttendees(Integer.valueOf(eventData.get("maxAttendees").toString()));
            }

            if (eventData.containsKey("isRecurring") && eventData.get("isRecurring") != null) {
                event.setIsRecurring(Boolean.parseBoolean(eventData.get("isRecurring").toString()));
            }

            if (eventData.containsKey("recurrencePattern") && eventData.get("recurrencePattern") != null) {
                event.setRecurrencePattern((String) eventData.get("recurrencePattern"));
            }

            Event savedEvent = eventRepository.save(event);

            return ResponseEntity.ok(Map.of("id", savedEvent.getId(), "message", "Event created successfully and pending approval"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create event: " + e.getMessage()));
        }
    }

    // Get all approved events
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedEvents() {
        try {
            List<Event> events = eventRepository.findByStatusAndHiddenOrderByEventDateAsc(ApprovalStatus.APPROVED, false);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                Map<String, Object> eventData = buildEventResponse(event);

                // Add registration count
                long registeredCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
                long waitlistCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.WAITLISTED);
                eventData.put("registeredCount", registeredCount);
                eventData.put("waitlistCount", waitlistCount);
                eventData.put("spotsAvailable", event.getMaxAttendees() != null ? event.getMaxAttendees() - registeredCount : null);

                response.add(eventData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get upcoming approved events
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingEvents() {
        try {
            List<Event> events = eventRepository.findByStatusAndEventDateAfterOrderByEventDateAsc(
                ApprovalStatus.APPROVED,
                LocalDateTime.now()
            );

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                if (!event.isHidden()) {
                    Map<String, Object> eventData = buildEventResponse(event);

                    long registeredCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
                    eventData.put("registeredCount", registeredCount);
                    eventData.put("spotsAvailable", event.getMaxAttendees() != null ? event.getMaxAttendees() - registeredCount : null);

                    response.add(eventData);
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get past approved events
    @GetMapping("/past")
    public ResponseEntity<?> getPastEvents() {
        try {
            List<Event> events = eventRepository.findByStatusAndEventDateBeforeOrderByEventDateDesc(
                ApprovalStatus.APPROVED,
                LocalDateTime.now()
            );

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                if (!event.isHidden()) {
                    response.add(buildEventResponse(event));
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get events by category
    @GetMapping("/category/{category}")
    public ResponseEntity<?> getEventsByCategory(@PathVariable String category) {
        try {
            List<Event> events = eventRepository.findByStatusAndCategoryOrderByEventDateAsc(ApprovalStatus.APPROVED, category);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                if (!event.isHidden()) {
                    response.add(buildEventResponse(event));
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get event by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable Long id) {
        try {
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            Map<String, Object> response = buildEventResponse(event);

            // Add registration stats
            long registeredCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
            long waitlistCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.WAITLISTED);
            response.put("registeredCount", registeredCount);
            response.put("waitlistCount", waitlistCount);
            response.put("spotsAvailable", event.getMaxAttendees() != null ? event.getMaxAttendees() - registeredCount : null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get events by creator
    @GetMapping("/my-events/{creatorId}")
    public ResponseEntity<?> getMyEvents(@PathVariable Long creatorId) {
        try {
            List<Event> events = eventRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                Map<String, Object> eventData = buildEventResponse(event);

                // Add registration count
                long registeredCount = registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.REGISTERED);
                eventData.put("registeredCount", registeredCount);

                response.add(eventData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Update event (only creator can edit)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Map<String, Object> eventData) {
        try {
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Check if user is the creator
            Long userId = Long.valueOf(eventData.get("userId").toString());
            if (!event.getCreatorId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the event creator can edit this event"));
            }

            // Only allow editing if status is PENDING
            if (event.getStatus() != ApprovalStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot edit event after it has been reviewed"));
            }

            // Update fields
            if (eventData.containsKey("title")) event.setTitle((String) eventData.get("title"));
            if (eventData.containsKey("description")) event.setDescription((String) eventData.get("description"));
            if (eventData.containsKey("imageUrl")) event.setImageUrl((String) eventData.get("imageUrl"));
            if (eventData.containsKey("category")) event.setCategory((String) eventData.get("category"));
            if (eventData.containsKey("location")) event.setLocation((String) eventData.get("location"));
            if (eventData.containsKey("organizerName")) event.setOrganizerName((String) eventData.get("organizerName"));

            if (eventData.containsKey("eventDate")) {
                event.setEventDate(LocalDateTime.parse(((String) eventData.get("eventDate")).replace("Z", "")));
            }
            if (eventData.containsKey("eventTime")) {
                event.setEventTime(LocalDateTime.parse(((String) eventData.get("eventTime")).replace("Z", "")));
            }
            if (eventData.containsKey("endTime")) {
                event.setEndTime(LocalDateTime.parse(((String) eventData.get("endTime")).replace("Z", "")));
            }
            if (eventData.containsKey("registrationDeadline") && eventData.get("registrationDeadline") != null) {
                event.setRegistrationDeadline(LocalDateTime.parse(((String) eventData.get("registrationDeadline")).replace("Z", "")));
            }
            if (eventData.containsKey("maxAttendees")) {
                event.setMaxAttendees(eventData.get("maxAttendees") != null ? Integer.valueOf(eventData.get("maxAttendees").toString()) : null);
            }

            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Update event image only
    @PutMapping("/{id}/image")
    public ResponseEntity<?> updateEventImage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            String imageUrl = body.get("imageUrl");
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Image URL is required"));
            }

            event.setImageUrl(imageUrl);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event image updated successfully", "imageUrl", imageUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete event (only creator can delete, only if PENDING)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id, @RequestParam Long userId) {
        try {
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Check if user is the creator
            if (!event.getCreatorId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the event creator can delete this event"));
            }

            // Only allow deletion if status is PENDING
            if (event.getStatus() != ApprovalStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete event after it has been reviewed"));
            }

            eventRepository.delete(event);

            return ResponseEntity.ok(Map.of("message", "Event deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Register for event
    @PostMapping("/{eventId}/register")
    public ResponseEntity<?> registerForEvent(@PathVariable Long eventId, @RequestBody Map<String, Object> registrationData) {
        try {
            Long userId = Long.valueOf(registrationData.get("userId").toString());

            // Check if event exists and is approved
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            if (event.getStatus() != ApprovalStatus.APPROVED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Event is not approved"));
            }

            // Check if event has already passed
            if (LocalDateTime.now().isAfter(event.getEventDate())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Event has already passed"));
            }

            // Check registration deadline
            if (event.getRegistrationDeadline() != null && LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Registration deadline has passed"));
            }

            // Check if already registered
            Optional<EventRegistration> existingRegistration = registrationRepository.findByEventIdAndUserId(eventId, userId);
            if (existingRegistration.isPresent() && existingRegistration.get().getStatus() != RegistrationStatus.CANCELLED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Already registered for this event"));
            }

            // Check capacity and determine if registration or waitlist
            RegistrationStatus status = RegistrationStatus.REGISTERED;
            if (event.getMaxAttendees() != null) {
                long currentRegistrations = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
                if (currentRegistrations >= event.getMaxAttendees()) {
                    status = RegistrationStatus.WAITLISTED;
                }
            }

            // Create registration
            EventRegistration registration = new EventRegistration(eventId, userId, status);
            if (status == RegistrationStatus.WAITLISTED) {
                registration.setMovedToWaitlistAt(LocalDateTime.now());
            }
            registrationRepository.save(registration);

            String message = status == RegistrationStatus.REGISTERED ?
                "Successfully registered for event" :
                "Event is full. You have been added to the waitlist";

            return ResponseEntity.ok(Map.of("message", message, "status", status.toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Cancel registration
    @PostMapping("/{eventId}/cancel-registration")
    public ResponseEntity<?> cancelRegistration(@PathVariable Long eventId, @RequestParam Long userId) {
        try {
            EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

            if (registration.getStatus() == RegistrationStatus.CANCELLED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Registration already cancelled"));
            }

            RegistrationStatus previousStatus = registration.getStatus();
            registration.setStatus(RegistrationStatus.CANCELLED);
            registration.setCancelledAt(LocalDateTime.now());
            registrationRepository.save(registration);

            // If user was registered (not waitlisted), move first waitlisted user to registered
            if (previousStatus == RegistrationStatus.REGISTERED) {
                List<EventRegistration> waitlist = registrationRepository.findByEventIdAndStatusOrderByMovedToWaitlistAtAsc(
                    eventId, RegistrationStatus.WAITLISTED
                );
                if (!waitlist.isEmpty()) {
                    EventRegistration firstWaitlisted = waitlist.get(0);
                    firstWaitlisted.setStatus(RegistrationStatus.REGISTERED);
                    firstWaitlisted.setMovedFromWaitlistAt(LocalDateTime.now());
                    registrationRepository.save(firstWaitlisted);
                }
            }

            return ResponseEntity.ok(Map.of("message", "Registration cancelled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get registrations for an event (for creator)
    @GetMapping("/{eventId}/registrations")
    public ResponseEntity<?> getEventRegistrations(@PathVariable Long eventId, @RequestParam Long creatorId) {
        try {
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Verify creator
            if (!event.getCreatorId().equals(creatorId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            List<EventRegistration> registrations = registrationRepository.findByEventIdOrderByRegisteredAtAsc(eventId);

            List<Map<String, Object>> response = new ArrayList<>();
            for (EventRegistration registration : registrations) {
                if (registration.getStatus() != RegistrationStatus.CANCELLED) {
                    User user = userRepository.findById(registration.getUserId()).orElse(null);
                    Map<String, Object> registrationData = new HashMap<>();
                    registrationData.put("id", registration.getId());
                    registrationData.put("userId", registration.getUserId());
                    registrationData.put("status", registration.getStatus().toString());
                    registrationData.put("registeredAt", registration.getRegisteredAt().toString());

                    if (user != null) {
                        registrationData.put("userName", user.getFirstName() + " " + user.getLastName());
                        registrationData.put("userEmail", user.getEmail());
                    }

                    response.add(registrationData);
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Check if user is registered
    @GetMapping("/{eventId}/is-registered")
    public ResponseEntity<?> isUserRegistered(@PathVariable Long eventId, @RequestParam Long userId) {
        try {
            Optional<EventRegistration> registration = registrationRepository.findByEventIdAndUserId(eventId, userId);
            boolean isRegistered = registration.isPresent() && registration.get().getStatus() == RegistrationStatus.REGISTERED;
            boolean isWaitlisted = registration.isPresent() && registration.get().getStatus() == RegistrationStatus.WAITLISTED;

            return ResponseEntity.ok(Map.of(
                "isRegistered", isRegistered,
                "isWaitlisted", isWaitlisted,
                "status", registration.isPresent() ? registration.get().getStatus().toString() : "NOT_REGISTERED"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get user's registered events
    @GetMapping("/user/{userId}/registered")
    public ResponseEntity<?> getUserRegisteredEvents(@PathVariable Long userId) {
        try {
            List<EventRegistration> registrations = registrationRepository.findByUserIdAndStatusOrderByRegisteredAtDesc(
                userId, RegistrationStatus.REGISTERED
            );

            List<Map<String, Object>> response = new ArrayList<>();
            for (EventRegistration registration : registrations) {
                Event event = eventRepository.findById(registration.getEventId()).orElse(null);
                if (event != null && event.getStatus() == ApprovalStatus.APPROVED && !event.isHidden()) {
                    Map<String, Object> eventData = buildEventResponse(event);
                    eventData.put("registrationId", registration.getId());
                    eventData.put("registeredAt", registration.getRegisteredAt().toString());
                    response.add(eventData);
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get event comments
    @GetMapping("/{eventId}/comments")
    public ResponseEntity<?> getEventComments(@PathVariable Long eventId) {
        try {
            List<EventComment> comments = commentRepository.findByEventIdAndIsDeletedOrderByCreatedAtDesc(eventId, false);

            List<Map<String, Object>> response = new ArrayList<>();
            for (EventComment comment : comments) {
                User user = userRepository.findById(comment.getUserId()).orElse(null);
                Map<String, Object> commentData = new HashMap<>();
                commentData.put("id", comment.getId());
                commentData.put("comment", comment.getComment());
                commentData.put("createdAt", comment.getCreatedAt().toString());
                commentData.put("updatedAt", comment.getUpdatedAt().toString());
                commentData.put("userId", comment.getUserId());

                if (user != null) {
                    commentData.put("userName", user.getFirstName() + " " + user.getLastName());
                }

                response.add(commentData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Add comment to event
    @PostMapping("/{eventId}/comments")
    public ResponseEntity<?> addEventComment(@PathVariable Long eventId, @RequestBody Map<String, Object> commentData) {
        try {
            // Verify event exists
            eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            Long userId = Long.valueOf(commentData.get("userId").toString());
            String commentText = (String) commentData.get("comment");

            if (commentText == null || commentText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comment cannot be empty"));
            }

            EventComment comment = new EventComment(eventId, userId, commentText);
            EventComment savedComment = commentRepository.save(comment);

            return ResponseEntity.ok(Map.of("id", savedComment.getId(), "message", "Comment added successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete comment (soft delete)
    @DeleteMapping("/{eventId}/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long eventId, @PathVariable Long commentId, @RequestParam Long userId) {
        try {
            EventComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

            // Check if user owns the comment
            if (!comment.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            comment.setIsDeleted(true);
            commentRepository.save(comment);

            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Get pending events
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingEvents(@RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            List<Event> events = eventRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Event event : events) {
                Map<String, Object> eventData = buildEventResponse(event);

                // Add creator info
                User creator = userRepository.findById(event.getCreatorId()).orElse(null);
                if (creator != null) {
                    eventData.put("creatorName", creator.getFirstName() + " " + creator.getLastName());
                    eventData.put("creatorEmail", creator.getEmail());
                }

                response.add(eventData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Approve event
    @PostMapping("/{eventId}/approve")
    public ResponseEntity<?> approveEvent(@PathVariable Long eventId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Validate status transition
            if (event.getStatus() != ApprovalStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only pending events can be approved"));
            }

            event.setStatus(ApprovalStatus.APPROVED);
            event.setApprovedAt(LocalDateTime.now());
            event.setApprovedBy(adminId);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event approved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Reject event
    @PostMapping("/{eventId}/reject")
    public ResponseEntity<?> rejectEvent(@PathVariable Long eventId, @RequestParam Long adminId, @RequestBody Map<String, String> data) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Validate status transition
            if (event.getStatus() != ApprovalStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only pending events can be rejected"));
            }

            // Validate rejection reason
            String reason = data.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }

            event.setStatus(ApprovalStatus.REJECTED);
            event.setRejectionReason(reason);
            event.setRejectedAt(LocalDateTime.now());
            event.setRejectedBy(adminId);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Hide/Delete event (soft delete)
    @PostMapping("/{eventId}/hide")
    public ResponseEntity<?> hideEvent(@PathVariable Long eventId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            event.setHidden(true);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event hidden successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Unhide event
    @PostMapping("/{eventId}/unhide")
    public ResponseEntity<?> unhideEvent(@PathVariable Long eventId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            event.setHidden(false);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event unhidden successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Helper method to build event response
    private Map<String, Object> buildEventResponse(Event event) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("id", event.getId());
        eventData.put("title", event.getTitle());
        eventData.put("description", event.getDescription());
        eventData.put("imageUrl", event.getImageUrl());
        eventData.put("eventDate", event.getEventDate().toString());
        eventData.put("eventTime", event.getEventTime().toString());
        eventData.put("endTime", event.getEndTime().toString());
        eventData.put("registrationDeadline", event.getRegistrationDeadline() != null ? event.getRegistrationDeadline().toString() : null);
        eventData.put("category", event.getCategory());
        eventData.put("location", event.getLocation());
        eventData.put("organizerName", event.getOrganizerName());
        eventData.put("maxAttendees", event.getMaxAttendees());
        eventData.put("status", event.getStatus().toString());
        eventData.put("creatorId", event.getCreatorId());
        eventData.put("createdAt", event.getCreatedAt().toString());
        eventData.put("rejectionReason", event.getRejectionReason());
        eventData.put("isRecurring", event.getIsRecurring());
        eventData.put("recurrencePattern", event.getRecurrencePattern());
        return eventData;
    }
}
