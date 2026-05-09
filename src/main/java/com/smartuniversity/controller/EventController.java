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
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

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

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private com.smartuniversity.repository.EventAttendanceRepository attendanceRepository;

    // Create a new event
    @PostMapping
    @Transactional
    public ResponseEntity<?> createEvent(@RequestBody Map<String, Object> eventData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            // Security: use authenticated user's ID, ignore any creatorId sent by client
            Long creatorId = currentUser.getId();

            Event event = new Event();
            event.setTitle((String) eventData.get("title"));
            event.setDescription((String) eventData.get("description"));
            event.setImageUrl((String) eventData.get("imageUrl"));
            event.setCategory((String) eventData.get("category"));
            event.setLocation((String) eventData.get("location"));
            event.setOrganizerName((String) eventData.get("organizerName"));
            event.setCreatorId(creatorId);

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
                LocalDateTime registrationDeadline = LocalDateTime.parse(
                    ((String) eventData.get("registrationDeadline")).replace("Z", "")
                );
                if (registrationDeadline.isAfter(event.getEventDate())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Registration deadline must be before event date"));
                }
                event.setRegistrationDeadline(registrationDeadline);
            }

            // Validate dates
            if (event.getEventDate().isAfter(event.getEndTime())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Event date must be before end time"));
            }

            if (eventData.containsKey("maxAttendees") && eventData.get("maxAttendees") != null) {
                Integer maxAttendees = Integer.valueOf(eventData.get("maxAttendees").toString());
                if (maxAttendees != null && maxAttendees < 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "maxAttendees cannot be negative"));
                }
                event.setMaxAttendees(maxAttendees);
            }

            if (eventData.containsKey("isRecurring") && eventData.get("isRecurring") != null) {
                event.setIsRecurring(Boolean.parseBoolean(eventData.get("isRecurring").toString()));
            }

            if (eventData.containsKey("recurrencePattern") && eventData.get("recurrencePattern") != null) {
                event.setRecurrencePattern((String) eventData.get("recurrencePattern"));
            }

            // Faculty auto-approval: Faculty members are Verified Creators
            String message;
            if (currentUser.getRole() == User.UserRole.FACULTY) {
                event.setStatus(ApprovalStatus.APPROVED);
                event.setApprovedAt(LocalDateTime.now());
                event.setApprovedBy(creatorId);
                message = "Event created and auto-approved (Faculty Verified Creator)";
            } else {
                message = "Event created successfully and pending approval";
            }

            Event savedEvent = eventRepository.save(event);

            return ResponseEntity.ok(Map.of("id", savedEvent.getId(), "message", message, "status", savedEvent.getStatus().toString()));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create event: " + msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Update event (only creator can edit)
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Map<String, Object> eventData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Check if user is the creator
            if (!event.getCreatorId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the event creator can edit this event"));
            }

            // Allow editing if status is PENDING or REJECTED (for resubmission)
            if (event.getStatus() != ApprovalStatus.PENDING && event.getStatus() != ApprovalStatus.REJECTED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot edit event after it has been approved"));
            }

            // If editing a REJECTED event, reset to PENDING for re-review
            boolean wasRejected = event.getStatus() == ApprovalStatus.REJECTED;
            if (wasRejected) {
                // Check if creator is Faculty - auto-approve on resubmit
                if (currentUser.getRole() == User.UserRole.FACULTY) {
                    event.setStatus(ApprovalStatus.APPROVED);
                    event.setApprovedAt(LocalDateTime.now());
                    event.setApprovedBy(currentUser.getId());
                } else {
                    event.setStatus(ApprovalStatus.PENDING);
                }
                // Clear previous rejection info
                event.setRejectionReason(null);
                event.setRejectedAt(null);
                event.setRejectedBy(null);
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
                Integer maxAttendees = eventData.get("maxAttendees") != null ? Integer.valueOf(eventData.get("maxAttendees").toString()) : null;
                if (maxAttendees != null && maxAttendees < 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "maxAttendees cannot be negative"));
                }
                event.setMaxAttendees(maxAttendees);
            }

            Event savedEvent = eventRepository.save(event);

            String message = wasRejected ?
                (savedEvent.getStatus() == ApprovalStatus.APPROVED ?
                    "Event resubmitted and auto-approved (Faculty Verified Creator)" :
                    "Event resubmitted and pending approval") :
                "Event updated successfully";

            return ResponseEntity.ok(Map.of("message", message, "status", savedEvent.getStatus().toString()));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Update event image only
    @PutMapping("/{id}/image")
    public ResponseEntity<?> updateEventImage(@PathVariable Long id, @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            String imageUrl = body.get("imageUrl");
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Image URL is required"));
            }

            event.setImageUrl(imageUrl);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event image updated successfully", "imageUrl", imageUrl));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Delete event (only creator can delete, only if PENDING)
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteEvent(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Check if user is the creator
            if (!event.getCreatorId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the event creator can delete this event"));
            }

            // Only allow deletion if status is PENDING
            if (event.getStatus() != ApprovalStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete event after it has been reviewed"));
            }

            eventRepository.delete(event);

            return ResponseEntity.ok(Map.of("message", "Event deleted successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Register for event
    @PostMapping("/{eventId}/register")
    @Transactional
    public ResponseEntity<?> registerForEvent(@PathVariable Long eventId, @RequestBody Map<String, Object> registrationData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            Long userId = currentUser.getId();

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
            Integer waitlistPosition = null;
            if (status == RegistrationStatus.WAITLISTED) {
                registration.setMovedToWaitlistAt(LocalDateTime.now());
                // Calculate waitlist position (1-based)
                long currentWaitlistCount = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED);
                waitlistPosition = (int) currentWaitlistCount + 1;
                registration.setWaitlistPosition(waitlistPosition);
            }
            registrationRepository.save(registration);

            String message = status == RegistrationStatus.REGISTERED ?
                "Successfully registered for event" :
                "Event is full. You have been added to the waitlist at position #" + waitlistPosition;

            Map<String, Object> response = new HashMap<>();
            response.put("message", message);
            response.put("status", status.toString());
            if (waitlistPosition != null) {
                response.put("waitlistPosition", waitlistPosition);
            }
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Cancel registration
    @PostMapping("/{eventId}/cancel-registration")
    @Transactional
    public ResponseEntity<?> cancelRegistration(@PathVariable Long eventId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, currentUser.getId())
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
                    firstWaitlisted.setWaitlistPosition(null); // Clear waitlist position
                    registrationRepository.save(firstWaitlisted);

                    // Update positions for remaining waitlisted users
                    for (int i = 1; i < waitlist.size(); i++) {
                        EventRegistration waitlistedUser = waitlist.get(i);
                        waitlistedUser.setWaitlistPosition(i); // Positions are 1-based
                        registrationRepository.save(waitlistedUser);
                    }
                }
            } else if (previousStatus == RegistrationStatus.WAITLISTED) {
                // User was on waitlist, update positions for remaining waitlisted users
                Integer cancelledPosition = registration.getWaitlistPosition();
                registration.setWaitlistPosition(null); // Clear cancelled user's position
                registrationRepository.save(registration);

                if (cancelledPosition != null) {
                    List<EventRegistration> waitlist = registrationRepository.findByEventIdAndStatusOrderByMovedToWaitlistAtAsc(
                        eventId, RegistrationStatus.WAITLISTED
                    );
                    int position = 1;
                    for (EventRegistration waitlistedUser : waitlist) {
                        waitlistedUser.setWaitlistPosition(position++);
                        registrationRepository.save(waitlistedUser);
                    }
                }
            }

            return ResponseEntity.ok(Map.of("message", "Registration cancelled successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Check if user is registered
    @GetMapping("/{eventId}/is-registered")
    public ResponseEntity<?> isUserRegistered(@PathVariable Long eventId, @RequestParam Long userId) {
        try {
            Optional<EventRegistration> registration = registrationRepository.findByEventIdAndUserId(eventId, userId);
            boolean isRegistered = registration.isPresent() && registration.get().getStatus() == RegistrationStatus.REGISTERED;
            boolean isWaitlisted = registration.isPresent() && registration.get().getStatus() == RegistrationStatus.WAITLISTED;

            Map<String, Object> response = new HashMap<>();
            response.put("isRegistered", isRegistered);
            response.put("isWaitlisted", isWaitlisted);
            response.put("status", registration.isPresent() ? registration.get().getStatus().toString() : "NOT_REGISTERED");

            // Include waitlist position if waitlisted
            if (isWaitlisted && registration.get().getWaitlistPosition() != null) {
                response.put("waitlistPosition", registration.get().getWaitlistPosition());
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Add comment to event
    @PostMapping("/{eventId}/comments")
    @Transactional
    public ResponseEntity<?> addEventComment(@PathVariable Long eventId,
            @RequestBody Map<String, Object> commentData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        // Verify event exists
        eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));

        String commentText = (String) commentData.get("comment");
        if (commentText == null || commentText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment cannot be empty"));
        }

        EventComment comment = new EventComment(eventId, currentUser.getId(), commentText);
        EventComment savedComment = commentRepository.save(comment);

        return ResponseEntity.ok(Map.of("id", savedComment.getId(), "message", "Comment added successfully"));
    }

    // Delete comment (soft delete)
    @DeleteMapping("/{eventId}/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long eventId, @PathVariable Long commentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = authUtils.getUserFromAuthHeader(authHeader);
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            EventComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

            // Check if user owns the comment or is admin
            boolean isAdmin = authUtils.isAdmin(authHeader);
            if (!comment.getUserId().equals(currentUser.getId()) && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            comment.setIsDeleted(true);
            commentRepository.save(comment);

            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Get pending events
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingEvents(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            User admin = authUtils.getAdminFromAuthHeader(authHeader);
            if (admin == null) {
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
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Approve event
    @PostMapping("/{eventId}/approve")
    public ResponseEntity<?> approveEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            User admin = authUtils.getAdminFromAuthHeader(authHeader);
            if (admin == null) {
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
            event.setApprovedBy(admin.getId());
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event approved successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Reject event
    @PostMapping("/{eventId}/reject")
    public ResponseEntity<?> rejectEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        try {
            // Verify admin role from JWT
            User admin = authUtils.getAdminFromAuthHeader(authHeader);
            if (admin == null) {
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
            event.setRejectedBy(admin.getId());
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event rejected"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Hide/Delete event (soft delete)
    @PostMapping("/{eventId}/hide")
    public ResponseEntity<?> hideEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            event.setHidden(true);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event hidden successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // Admin: Unhide event
    @PostMapping("/{eventId}/unhide")
    public ResponseEntity<?> unhideEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            event.setHidden(false);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of("message", "Event unhidden successfully"));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", msg));
            }
            if (msg != null && (msg.toLowerCase().contains("unauthorized") || msg.toLowerCase().contains("authentication required"))) {
                return ResponseEntity.status(401).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    // ============================================
    // ATTENDANCE TRACKING ENDPOINTS
    // ============================================

    /**
     * Mark attendance for a user at an event
     * Admin/Organizer only
     */
    @PostMapping("/{eventId}/attendance")
    public ResponseEntity<?> markAttendance(@PathVariable Long eventId, @RequestBody com.smartuniversity.dto.AttendanceRequest request) {
        try {
            // Validate event exists
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Validate user exists
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if already attended
            if (attendanceRepository.existsByEventIdAndUserId(eventId, request.getUserId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Attendance already marked for this user"));
            }

            // Get current user (who is marking attendance)
            Long checkedInBy = authUtils.getCurrentUserId();

            // Create attendance record
            com.smartuniversity.model.EventAttendance attendance = new com.smartuniversity.model.EventAttendance(event, user, checkedInBy);
            attendance.setNotes(request.getNotes());

            com.smartuniversity.model.EventAttendance saved = attendanceRepository.save(attendance);

            return ResponseEntity.ok(Map.of(
                "message", "Attendance marked successfully",
                "id", saved.getId(),
                "checkedInAt", saved.getCheckedInAt().toString()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all attendance for an event
     * Admin/Organizer only
     */
    @GetMapping("/{eventId}/attendance")
    public ResponseEntity<?> getEventAttendance(@PathVariable Long eventId) {
        try {
            // Validate event exists
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            List<com.smartuniversity.model.EventAttendance> attendanceList = attendanceRepository.findByEventId(eventId);

            List<com.smartuniversity.dto.AttendanceResponse> responses = new ArrayList<>();
            for (com.smartuniversity.model.EventAttendance attendance : attendanceList) {
                User user = attendance.getUser();
                User checkedInByUser = attendance.getCheckedInBy() != null ?
                    userRepository.findById(attendance.getCheckedInBy()).orElse(null) : null;

                com.smartuniversity.dto.AttendanceResponse response = new com.smartuniversity.dto.AttendanceResponse(
                    attendance.getId(),
                    event.getId(),
                    event.getTitle(),
                    user.getId(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmail(),
                    attendance.getCheckedInAt(),
                    attendance.getCheckedInBy(),
                    checkedInByUser != null ? checkedInByUser.getFirstName() + " " + checkedInByUser.getLastName() : null,
                    attendance.getNotes()
                );
                responses.add(response);
            }

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get attendance statistics for an event
     * Admin/Organizer only
     */
    @GetMapping("/{eventId}/attendance/stats")
    public ResponseEntity<?> getAttendanceStats(@PathVariable Long eventId) {
        try {
            // Validate event exists
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

            // Count registrations
            long totalRegistered = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
            long totalWaitlisted = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED);

            // Count attendance
            long totalAttended = attendanceRepository.countByEventId(eventId);

            // Calculate attendance rate
            double attendanceRate = totalRegistered > 0 ? (double) totalAttended / totalRegistered * 100 : 0.0;

            com.smartuniversity.dto.AttendanceStatsResponse stats = new com.smartuniversity.dto.AttendanceStatsResponse(
                event.getId(),
                event.getTitle(),
                totalRegistered,
                totalAttended,
                totalWaitlisted,
                Math.round(attendanceRate * 100.0) / 100.0  // Round to 2 decimal places
            );

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove attendance record
     * Admin only
     */
    @DeleteMapping("/{eventId}/attendance/{userId}")
    public ResponseEntity<?> removeAttendance(@PathVariable Long eventId, @PathVariable Long userId) {
        try {
            attendanceRepository.deleteByEventIdAndUserId(eventId, userId);
            return ResponseEntity.ok(Map.of("message", "Attendance record removed successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if user attended an event
     */
    @GetMapping("/{eventId}/attendance/check/{userId}")
    public ResponseEntity<?> checkAttendance(@PathVariable Long eventId, @PathVariable Long userId) {
        try {
            boolean attended = attendanceRepository.existsByEventIdAndUserId(eventId, userId);

            if (attended) {
                com.smartuniversity.model.EventAttendance attendance = attendanceRepository.findByEventIdAndUserId(eventId, userId)
                    .orElse(null);
                return ResponseEntity.ok(Map.of(
                    "attended", true,
                    "checkedInAt", attendance != null ? attendance.getCheckedInAt().toString() : null
                ));
            }

            return ResponseEntity.ok(Map.of("attended", false));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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
