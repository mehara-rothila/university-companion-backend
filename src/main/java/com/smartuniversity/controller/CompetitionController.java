package com.smartuniversity.controller;

import com.smartuniversity.model.Competition;
import com.smartuniversity.model.Competition.ApprovalStatus;
import com.smartuniversity.model.CompetitionEnrollment;
import com.smartuniversity.model.CompetitionEnrollment.EnrollmentStatus;
import com.smartuniversity.model.FormField;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.CompetitionEnrollmentRepository;
import com.smartuniversity.repository.CompetitionRepository;
import com.smartuniversity.repository.FormFieldRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.smartuniversity.exception.NotFoundException;
import com.smartuniversity.exception.UnauthorizedException;
import com.smartuniversity.exception.ForbiddenException;

@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private FormFieldRepository formFieldRepository;

    @Autowired
    private CompetitionEnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private S3Service s3Service;

    // Create a new competition
    @PostMapping
    @Transactional
    public ResponseEntity<?> createCompetition(@RequestBody Map<String, Object> competitionData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        // Security: use authenticated user's ID, ignore any organizerId sent by client
        Long organizerId = currentUser.getId();

        Competition competition = new Competition();
        competition.setTitle((String) competitionData.get("title"));
        competition.setDescription((String) competitionData.get("description"));
        competition.setImageUrl((String) competitionData.get("imageUrl"));
        competition.setCategory((String) competitionData.get("category"));
        competition.setLocation((String) competitionData.get("location"));
        competition.setPrizes((String) competitionData.get("prizes"));
        competition.setOrganizerId(organizerId);

        // Parse ISO 8601 date strings with timezone (e.g., "2025-10-16T10:57:00.000Z")
        competition.setStartDate(LocalDateTime.parse(
            ((String) competitionData.get("startDate")).replace("Z", "")
        ));
        competition.setEndDate(LocalDateTime.parse(
            ((String) competitionData.get("endDate")).replace("Z", "")
        ));

        if (competitionData.containsKey("registrationDeadline") && competitionData.get("registrationDeadline") != null) {
            LocalDateTime registrationDeadline = LocalDateTime.parse(
                ((String) competitionData.get("registrationDeadline")).replace("Z", "")
            );
            if (registrationDeadline.isAfter(competition.getStartDate())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Registration deadline must be before start date"));
            }
            competition.setRegistrationDeadline(registrationDeadline);
        }

        // Validate dates
        if (competition.getStartDate().isAfter(competition.getEndDate())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Start date must be before end date"));
        }

        if (competitionData.containsKey("maxParticipants") && competitionData.get("maxParticipants") != null) {
            Integer maxParticipants = Integer.valueOf(competitionData.get("maxParticipants").toString());
            if (maxParticipants != null && maxParticipants < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "maxParticipants cannot be negative"));
            }
            competition.setMaxParticipants(maxParticipants);
        }

        competition.setInternalEnrollmentEnabled(Boolean.parseBoolean(competitionData.get("internalEnrollmentEnabled").toString()));

        // Validate external enrollment URL if provided
        String externalUrl = (String) competitionData.get("externalEnrollmentUrl");
        if (externalUrl != null && !externalUrl.trim().isEmpty()) {
            if (!isValidUrl(externalUrl)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid external enrollment URL format"));
            }
        }
        competition.setExternalEnrollmentUrl(externalUrl);

        // Faculty auto-approval: Faculty members are Verified Creators
        String message;
        if (currentUser.getRole() == User.UserRole.FACULTY) {
            competition.setStatus(ApprovalStatus.APPROVED);
            competition.setApprovedAt(LocalDateTime.now());
            competition.setApprovedBy(organizerId);
            message = "Competition created and auto-approved (Faculty Verified Creator)";
        } else {
            message = "Competition created successfully and pending approval";
        }

        Competition savedCompetition = competitionRepository.save(competition);

        // Save form fields if internal enrollment is enabled
        if (competition.isInternalEnrollmentEnabled() && competitionData.containsKey("formFields")) {
            List<Map<String, Object>> formFieldsData = (List<Map<String, Object>>) competitionData.get("formFields");
            for (Map<String, Object> fieldData : formFieldsData) {
                FormField field = new FormField();
                field.setCompetitionId(savedCompetition.getId());
                field.setFieldLabel((String) fieldData.get("fieldLabel"));
                field.setFieldType(FormField.FieldType.valueOf(((String) fieldData.get("fieldType")).toUpperCase()));
                field.setRequired(Boolean.parseBoolean(fieldData.get("required").toString()));
                field.setOrder(Integer.valueOf(fieldData.get("order").toString()));
                field.setOptions((String) fieldData.get("options"));
                field.setPlaceholder((String) fieldData.get("placeholder"));
                formFieldRepository.save(field);
            }
        }

        return ResponseEntity.ok(Map.of("id", savedCompetition.getId(), "message", message, "status", savedCompetition.getStatus().toString()));
    }

    // Update competition (only organizer can edit)
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateCompetition(@PathVariable Long id, @RequestBody Map<String, Object> competitionData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        Competition competition = competitionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Check if user is the organizer
        if (!competition.getOrganizerId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the competition organizer can edit this competition"));
        }

        // Allow editing if status is PENDING or REJECTED (for resubmission)
        if (competition.getStatus() != ApprovalStatus.PENDING && competition.getStatus() != ApprovalStatus.REJECTED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot edit competition after it has been approved"));
        }

        // If editing a REJECTED competition, reset status for re-review
        boolean wasRejected = competition.getStatus() == ApprovalStatus.REJECTED;
        if (wasRejected) {
            // Check if organizer is Faculty - auto-approve on resubmit
            if (currentUser.getRole() == User.UserRole.FACULTY) {
                competition.setStatus(ApprovalStatus.APPROVED);
                competition.setApprovedAt(LocalDateTime.now());
                competition.setApprovedBy(currentUser.getId());
            } else {
                competition.setStatus(ApprovalStatus.PENDING);
            }
            // Clear previous rejection info
            competition.setRejectionReason(null);
            competition.setRejectedAt(null);
            competition.setRejectedBy(null);
        }

        // Update fields
        if (competitionData.containsKey("title")) competition.setTitle((String) competitionData.get("title"));
        if (competitionData.containsKey("description")) competition.setDescription((String) competitionData.get("description"));
        if (competitionData.containsKey("imageUrl")) competition.setImageUrl((String) competitionData.get("imageUrl"));
        if (competitionData.containsKey("category")) competition.setCategory((String) competitionData.get("category"));
        if (competitionData.containsKey("location")) competition.setLocation((String) competitionData.get("location"));
        if (competitionData.containsKey("prizes")) competition.setPrizes((String) competitionData.get("prizes"));

        if (competitionData.containsKey("startDate")) {
            competition.setStartDate(LocalDateTime.parse(((String) competitionData.get("startDate")).replace("Z", "")));
        }
        if (competitionData.containsKey("endDate")) {
            competition.setEndDate(LocalDateTime.parse(((String) competitionData.get("endDate")).replace("Z", "")));
        }
        if (competitionData.containsKey("registrationDeadline") && competitionData.get("registrationDeadline") != null) {
            competition.setRegistrationDeadline(LocalDateTime.parse(((String) competitionData.get("registrationDeadline")).replace("Z", "")));
        }
        if (competitionData.containsKey("maxParticipants")) {
            Integer maxParticipants = competitionData.get("maxParticipants") != null ? Integer.valueOf(competitionData.get("maxParticipants").toString()) : null;
            if (maxParticipants != null && maxParticipants < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "maxParticipants cannot be negative"));
            }
            competition.setMaxParticipants(maxParticipants);
        }

        Competition savedCompetition = competitionRepository.save(competition);

        String message = wasRejected ?
            (savedCompetition.getStatus() == ApprovalStatus.APPROVED ?
                "Competition resubmitted and auto-approved (Faculty Verified Creator)" :
                "Competition resubmitted and pending approval") :
            "Competition updated successfully";

        return ResponseEntity.ok(Map.of("message", message, "status", savedCompetition.getStatus().toString()));
    }

    // Get all approved competitions
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedCompetitions() {
        // Get all approved competitions ordered by start date (descending to show latest first)
        List<Competition> competitions = competitionRepository.findByStatusOrderByStartDateDesc(ApprovalStatus.APPROVED);

        List<Map<String, Object>> response = new ArrayList<>();
        for (Competition comp : competitions) {
            // Skip hidden competitions
            if (!comp.isHidden()) {
                Map<String, Object> compData = buildCompetitionResponse(comp);
                response.add(compData);
            }
        }

        return ResponseEntity.ok(response);
    }

    // Get competition by ID with form fields
    @GetMapping("/{id}")
    public ResponseEntity<?> getCompetitionById(@PathVariable Long id) {
        Competition competition = competitionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        Map<String, Object> response = buildCompetitionResponse(competition);

        // Add form fields if internal enrollment enabled
        if (competition.isInternalEnrollmentEnabled()) {
            List<FormField> formFields = formFieldRepository.findByCompetitionIdOrderByOrderAsc(id);
            response.put("formFields", formFields);
        }

        return ResponseEntity.ok(response);
    }

    // Admin: Update competition image only
    @PutMapping("/{id}/image")
    public ResponseEntity<?> updateCompetitionImage(@PathVariable Long id, @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image URL is required"));
        }

        competition.setImageUrl(imageUrl);
        competitionRepository.save(competition);

        return ResponseEntity.ok(Map.of("message", "Competition image updated successfully", "imageUrl", imageUrl));
    }

    // Get competitions by organizer
    @GetMapping("/my-competitions/{organizerId}")
    public ResponseEntity<?> getMyCompetitions(@PathVariable Long organizerId) {
        List<Competition> competitions = competitionRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId);

        List<Map<String, Object>> response = new ArrayList<>();
        for (Competition comp : competitions) {
            Map<String, Object> compData = buildCompetitionResponse(comp);

            // Add enrollment count if internal enrollment is enabled
            if (comp.isInternalEnrollmentEnabled()) {
                Long enrollmentCount = enrollmentRepository.countByCompetitionIdAndStatus(comp.getId(), EnrollmentStatus.ENROLLED);
                compData.put("enrollmentCount", enrollmentCount);
            }

            response.add(compData);
        }

        return ResponseEntity.ok(response);
    }

    // Enroll in competition
    @PostMapping("/{competitionId}/enroll")
    @Transactional
    public ResponseEntity<?> enrollInCompetition(@PathVariable Long competitionId, @RequestBody Map<String, Object> enrollmentData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        Long userId = currentUser.getId();

        // Check if competition exists and is approved
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        if (competition.getStatus() != ApprovalStatus.APPROVED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Competition is not approved"));
        }

        // Check if internal enrollment is enabled
        if (!competition.isInternalEnrollmentEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Internal enrollment is not enabled for this competition"));
        }

        // Check registration deadline
        if (competition.getRegistrationDeadline() != null && LocalDateTime.now().isAfter(competition.getRegistrationDeadline())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration deadline has passed"));
        }

        // Check if already enrolled
        if (enrollmentRepository.existsByCompetitionIdAndUserIdAndStatus(competitionId, userId, EnrollmentStatus.ENROLLED)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already enrolled in this competition"));
        }

        // Check max participants
        if (competition.getMaxParticipants() != null) {
            Long currentEnrollments = enrollmentRepository.countByCompetitionIdAndStatus(competitionId, EnrollmentStatus.ENROLLED);
            if (currentEnrollments >= competition.getMaxParticipants()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Competition is full"));
            }
        }

        // Create enrollment
        CompetitionEnrollment enrollment = new CompetitionEnrollment();
        enrollment.setCompetitionId(competitionId);
        enrollment.setUserId(userId);
        enrollment.setFormResponses((String) enrollmentData.get("formResponses")); // JSON string
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        enrollmentRepository.save(enrollment);

        return ResponseEntity.ok(Map.of("message", "Successfully enrolled in competition"));
    }

    // Withdraw from competition
    @PostMapping("/{competitionId}/withdraw")
    @Transactional
    public ResponseEntity<?> withdrawFromCompetition(@PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        CompetitionEnrollment enrollment = enrollmentRepository.findByCompetitionIdAndUserId(competitionId, currentUser.getId())
            .orElseThrow(() -> new NotFoundException("Enrollment not found"));

        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already withdrawn"));
        }

        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);

        return ResponseEntity.ok(Map.of("message", "Successfully withdrawn from competition"));
    }

    // Get enrollments for a competition (for organizer)
    @GetMapping("/{competitionId}/enrollments")
    public ResponseEntity<?> getCompetitionEnrollments(
            @PathVariable Long competitionId,
            @RequestParam(required = false) Long organizerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Allow organizer or admin
        boolean isAdmin = authUtils.isAdmin(authHeader);
        boolean isOrganizer = organizerId != null && competition.getOrganizerId().equals(organizerId);
        if (!isAdmin && !isOrganizer) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<CompetitionEnrollment> enrollments = enrollmentRepository.findByCompetitionIdAndStatusOrderByEnrolledAtAsc(
            competitionId,
            EnrollmentStatus.ENROLLED
        );

        // Batch fetch users to avoid N+1 queries
        Set<Long> userIds = new HashSet<>();
        for (CompetitionEnrollment enrollment : enrollments) {
            userIds.add(enrollment.getUserId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userMap.put(u.getId(), u);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (CompetitionEnrollment enrollment : enrollments) {
            User user = userMap.get(enrollment.getUserId());
            Map<String, Object> enrollmentData = new HashMap<>();
            enrollmentData.put("id", enrollment.getId());
            enrollmentData.put("userId", enrollment.getUserId());
            enrollmentData.put("formResponses", enrollment.getFormResponses());
            enrollmentData.put("enrolledAt", enrollment.getEnrolledAt().toString());

            if (user != null) {
                enrollmentData.put("userName", user.getFirstName() + " " + user.getLastName());
                enrollmentData.put("userEmail", user.getEmail());
            }

            response.add(enrollmentData);
        }

        return ResponseEntity.ok(response);
    }

    // Export enrollments as CSV
    @GetMapping("/{competitionId}/enrollments/export")
    public ResponseEntity<?> exportEnrollments(
            @PathVariable Long competitionId,
            @RequestParam(required = false) Long organizerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Allow organizer or admin
        boolean isAdmin = authUtils.isAdmin(authHeader);
        boolean isOrganizer = organizerId != null && competition.getOrganizerId().equals(organizerId);
        if (!isAdmin && !isOrganizer) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<CompetitionEnrollment> enrollments = enrollmentRepository.findByCompetitionIdAndStatusOrderByEnrolledAtAsc(
            competitionId,
            EnrollmentStatus.ENROLLED
        );

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Name,Email,Enrolled At,Form Responses\n");

        // Batch fetch users to avoid N+1 queries
        Set<Long> userIds = new HashSet<>();
        for (CompetitionEnrollment enrollment : enrollments) {
            userIds.add(enrollment.getUserId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userMap.put(u.getId(), u);
        }

        for (CompetitionEnrollment enrollment : enrollments) {
            User user = userMap.get(enrollment.getUserId());
            csv.append(enrollment.getUserId()).append(",");

            if (user != null) {
                csv.append("\"").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\",");
                csv.append("\"").append(user.getEmail()).append("\",");
            } else {
                csv.append("N/A,N/A,");
            }

            csv.append("\"").append(enrollment.getEnrolledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",");
            csv.append("\"").append(enrollment.getFormResponses() != null ? enrollment.getFormResponses().replace("\"", "\"\"") : "").append("\"\n");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "enrollments_" + competitionId + ".csv");

        return new ResponseEntity<>(csv.toString(), headers, HttpStatus.OK);
    }

    // Check if current user is enrolled
    @GetMapping("/{competitionId}/is-enrolled")
    public ResponseEntity<?> isUserEnrolled(@PathVariable Long competitionId) {
        Long userId = authUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        boolean isEnrolled = enrollmentRepository.existsByCompetitionIdAndUserIdAndStatus(
            competitionId,
            userId,
            EnrollmentStatus.ENROLLED
        );
        return ResponseEntity.ok(Map.of("isEnrolled", isEnrolled));
    }

    // Admin: Get pending competitions
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingCompetitions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        List<Competition> competitions = competitionRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);

        // Batch fetch organizers to avoid N+1 queries
        Set<Long> organizerIds = new HashSet<>();
        for (Competition comp : competitions) {
            organizerIds.add(comp.getOrganizerId());
        }
        Map<Long, User> organizerMap = new HashMap<>();
        for (User u : userRepository.findAllById(organizerIds)) {
            organizerMap.put(u.getId(), u);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Competition comp : competitions) {
            Map<String, Object> compData = buildCompetitionResponse(comp);

            // Add organizer info
            User organizer = organizerMap.get(comp.getOrganizerId());
            if (organizer != null) {
                compData.put("organizerName", organizer.getFirstName() + " " + organizer.getLastName());
                compData.put("organizerEmail", organizer.getEmail());
            }

            response.add(compData);
        }

        return ResponseEntity.ok(response);
    }

    // Admin: Approve competition
    @PostMapping("/{competitionId}/approve")
    public ResponseEntity<?> approveCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        User admin = authUtils.getAdminFromAuthHeader(authHeader);
        if (admin == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Validate status transition
        if (competition.getStatus() != ApprovalStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only pending competitions can be approved"));
        }

        competition.setStatus(ApprovalStatus.APPROVED);
        competition.setApprovedAt(LocalDateTime.now());
        competition.setApprovedBy(admin.getId());
        competitionRepository.save(competition);

        return ResponseEntity.ok(Map.of("message", "Competition approved successfully"));
    }

    // Admin: Reject competition
    @PostMapping("/{competitionId}/reject")
    public ResponseEntity<?> rejectCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        // Verify admin role from JWT
        User admin = authUtils.getAdminFromAuthHeader(authHeader);
        if (admin == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Validate status transition
        if (competition.getStatus() != ApprovalStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only pending competitions can be rejected"));
        }

        // Validate rejection reason
        String reason = data.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
        }

        competition.setStatus(ApprovalStatus.REJECTED);
        competition.setRejectionReason(reason);
        competition.setRejectedAt(LocalDateTime.now());
        competition.setRejectedBy(admin.getId());
        competitionRepository.save(competition);

        return ResponseEntity.ok(Map.of("message", "Competition rejected"));
    }

    // Admin: Hide/Delete competition (soft delete)
    @PostMapping("/{competitionId}/hide")
    public ResponseEntity<?> hideCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        competition.setHidden(true);
        competitionRepository.save(competition);

        return ResponseEntity.ok(Map.of("message", "Competition hidden successfully"));
    }

    // Admin: Unhide competition
    @PostMapping("/{competitionId}/unhide")
    public ResponseEntity<?> unhideCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        competition.setHidden(false);
        competitionRepository.save(competition);

        return ResponseEntity.ok(Map.of("message", "Competition unhidden successfully"));
    }

    // Admin: Get all competitions (all statuses)
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllCompetitions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        List<Competition> competitions = competitionRepository.findAllByOrderByCreatedAtDesc();

        // Batch fetch organizers to avoid N+1 queries
        Set<Long> organizerIds = new HashSet<>();
        for (Competition comp : competitions) {
            organizerIds.add(comp.getOrganizerId());
        }
        Map<Long, User> organizerMap = new HashMap<>();
        for (User u : userRepository.findAllById(organizerIds)) {
            organizerMap.put(u.getId(), u);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Competition comp : competitions) {
            Map<String, Object> compData = buildCompetitionResponse(comp);

            // Add organizer info
            User organizer = organizerMap.get(comp.getOrganizerId());
            if (organizer != null) {
                compData.put("organizerName", organizer.getFirstName() + " " + organizer.getLastName());
                compData.put("organizerEmail", organizer.getEmail());
            }

            // Add enrollment count
            long enrollmentCount = enrollmentRepository.countByCompetitionIdAndStatus(
                comp.getId(),
                EnrollmentStatus.ENROLLED
            );
            compData.put("enrollmentCount", enrollmentCount);

            response.add(compData);
        }

        return ResponseEntity.ok(response);
    }

    // Admin: Delete competition permanently
    @DeleteMapping("/{competitionId}")
    public ResponseEntity<?> deleteCompetition(
            @PathVariable Long competitionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        Competition competition = competitionRepository.findById(competitionId)
            .orElseThrow(() -> new NotFoundException("Competition not found"));

        // Delete related enrollments first
        enrollmentRepository.deleteByCompetitionId(competitionId);

        // Delete related form fields
        formFieldRepository.deleteByCompetitionId(competitionId);

        // Delete the competition
        competitionRepository.delete(competition);

        return ResponseEntity.ok(Map.of("message", "Competition deleted successfully"));
    }

    // Helper method to validate URL format
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String protocol = urlObj.getProtocol();
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    // Helper method to build competition response
    private Map<String, Object> buildCompetitionResponse(Competition competition) {
        Map<String, Object> compData = new HashMap<>();
        compData.put("id", competition.getId());
        compData.put("title", competition.getTitle());
        compData.put("description", competition.getDescription());
        compData.put("imageUrl", competition.getImageUrl());
        compData.put("startDate", competition.getStartDate().toString());
        compData.put("endDate", competition.getEndDate().toString());
        compData.put("registrationDeadline", competition.getRegistrationDeadline() != null ? competition.getRegistrationDeadline().toString() : null);
        compData.put("category", competition.getCategory());
        compData.put("location", competition.getLocation());
        compData.put("prizes", competition.getPrizes());
        compData.put("maxParticipants", competition.getMaxParticipants());
        compData.put("status", competition.getStatus().toString());
        compData.put("organizerId", competition.getOrganizerId());
        compData.put("internalEnrollmentEnabled", competition.isInternalEnrollmentEnabled());
        compData.put("externalEnrollmentUrl", competition.getExternalEnrollmentUrl());
        compData.put("createdAt", competition.getCreatedAt().toString());
        compData.put("rejectionReason", competition.getRejectionReason());
        return compData;
    }
}
