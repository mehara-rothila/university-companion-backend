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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
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
    private S3Service s3Service;

    // Create a new competition
    @PostMapping
    public ResponseEntity<?> createCompetition(@RequestBody Map<String, Object> competitionData) {
        try {
            Competition competition = new Competition();
            competition.setTitle((String) competitionData.get("title"));
            competition.setDescription((String) competitionData.get("description"));
            competition.setImageUrl((String) competitionData.get("imageUrl"));
            competition.setCategory((String) competitionData.get("category"));
            competition.setLocation((String) competitionData.get("location"));
            competition.setPrizes((String) competitionData.get("prizes"));
            competition.setOrganizerId(Long.valueOf(competitionData.get("organizerId").toString()));

            // Parse ISO 8601 date strings with timezone (e.g., "2025-10-16T10:57:00.000Z")
            competition.setStartDate(LocalDateTime.parse(
                ((String) competitionData.get("startDate")).replace("Z", "")
            ));
            competition.setEndDate(LocalDateTime.parse(
                ((String) competitionData.get("endDate")).replace("Z", "")
            ));

            if (competitionData.containsKey("registrationDeadline") && competitionData.get("registrationDeadline") != null) {
                competition.setRegistrationDeadline(LocalDateTime.parse(
                    ((String) competitionData.get("registrationDeadline")).replace("Z", "")
                ));
            }

            if (competitionData.containsKey("maxParticipants") && competitionData.get("maxParticipants") != null) {
                competition.setMaxParticipants(Integer.valueOf(competitionData.get("maxParticipants").toString()));
            }

            competition.setInternalEnrollmentEnabled(Boolean.parseBoolean(competitionData.get("internalEnrollmentEnabled").toString()));
            competition.setExternalEnrollmentUrl((String) competitionData.get("externalEnrollmentUrl"));

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

            return ResponseEntity.ok(Map.of("id", savedCompetition.getId(), "message", "Competition created successfully and pending approval"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create competition: " + e.getMessage()));
        }
    }

    // Get all approved competitions
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedCompetitions() {
        try {
            // Get all approved competitions ordered by start date (descending to show latest first)
            List<Competition> competitions = competitionRepository.findByStatusOrderByStartDateDesc(ApprovalStatus.APPROVED);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Competition comp : competitions) {
                Map<String, Object> compData = buildCompetitionResponse(comp);
                response.add(compData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get competition by ID with form fields
    @GetMapping("/{id}")
    public ResponseEntity<?> getCompetitionById(@PathVariable Long id) {
        try {
            Competition competition = competitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

            Map<String, Object> response = buildCompetitionResponse(competition);

            // Add form fields if internal enrollment enabled
            if (competition.isInternalEnrollmentEnabled()) {
                List<FormField> formFields = formFieldRepository.findByCompetitionIdOrderByOrderAsc(id);
                response.put("formFields", formFields);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get competitions by organizer
    @GetMapping("/my-competitions/{organizerId}")
    public ResponseEntity<?> getMyCompetitions(@PathVariable Long organizerId) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Enroll in competition
    @PostMapping("/{competitionId}/enroll")
    public ResponseEntity<?> enrollInCompetition(@PathVariable Long competitionId, @RequestBody Map<String, Object> enrollmentData) {
        try {
            Long userId = Long.valueOf(enrollmentData.get("userId").toString());

            // Check if competition exists and is approved
            Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Withdraw from competition
    @PostMapping("/{competitionId}/withdraw")
    public ResponseEntity<?> withdrawFromCompetition(@PathVariable Long competitionId, @RequestParam Long userId) {
        try {
            CompetitionEnrollment enrollment = enrollmentRepository.findByCompetitionIdAndUserId(competitionId, userId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

            if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
                return ResponseEntity.badRequest().body(Map.of("error", "Already withdrawn"));
            }

            enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
            enrollment.setWithdrawnAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);

            return ResponseEntity.ok(Map.of("message", "Successfully withdrawn from competition"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get enrollments for a competition (for organizer)
    @GetMapping("/{competitionId}/enrollments")
    public ResponseEntity<?> getCompetitionEnrollments(@PathVariable Long competitionId, @RequestParam Long organizerId) {
        try {
            Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

            // Verify organizer
            if (!competition.getOrganizerId().equals(organizerId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            List<CompetitionEnrollment> enrollments = enrollmentRepository.findByCompetitionIdAndStatusOrderByEnrolledAtAsc(
                competitionId,
                EnrollmentStatus.ENROLLED
            );

            List<Map<String, Object>> response = new ArrayList<>();
            for (CompetitionEnrollment enrollment : enrollments) {
                User user = userRepository.findById(enrollment.getUserId()).orElse(null);
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Export enrollments as CSV
    @GetMapping("/{competitionId}/enrollments/export")
    public ResponseEntity<?> exportEnrollments(@PathVariable Long competitionId, @RequestParam Long organizerId) {
        try {
            Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

            // Verify organizer
            if (!competition.getOrganizerId().equals(organizerId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            List<CompetitionEnrollment> enrollments = enrollmentRepository.findByCompetitionIdAndStatusOrderByEnrolledAtAsc(
                competitionId,
                EnrollmentStatus.ENROLLED
            );

            StringBuilder csv = new StringBuilder();
            csv.append("User ID,Name,Email,Enrolled At,Form Responses\n");

            for (CompetitionEnrollment enrollment : enrollments) {
                User user = userRepository.findById(enrollment.getUserId()).orElse(null);
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Check if user is enrolled
    @GetMapping("/{competitionId}/is-enrolled")
    public ResponseEntity<?> isUserEnrolled(@PathVariable Long competitionId, @RequestParam Long userId) {
        try {
            boolean isEnrolled = enrollmentRepository.existsByCompetitionIdAndUserIdAndStatus(
                competitionId,
                userId,
                EnrollmentStatus.ENROLLED
            );
            return ResponseEntity.ok(Map.of("isEnrolled", isEnrolled));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Get pending competitions
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingCompetitions(@RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            List<Competition> competitions = competitionRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);

            List<Map<String, Object>> response = new ArrayList<>();
            for (Competition comp : competitions) {
                Map<String, Object> compData = buildCompetitionResponse(comp);

                // Add organizer info
                User organizer = userRepository.findById(comp.getOrganizerId()).orElse(null);
                if (organizer != null) {
                    compData.put("organizerName", organizer.getFirstName() + " " + organizer.getLastName());
                    compData.put("organizerEmail", organizer.getEmail());
                }

                response.add(compData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Approve competition
    @PostMapping("/{competitionId}/approve")
    public ResponseEntity<?> approveCompetition(@PathVariable Long competitionId, @RequestParam Long adminId) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

            competition.setStatus(ApprovalStatus.APPROVED);
            competition.setApprovedAt(LocalDateTime.now());
            competition.setApprovedBy(adminId);
            competitionRepository.save(competition);

            return ResponseEntity.ok(Map.of("message", "Competition approved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Reject competition
    @PostMapping("/{competitionId}/reject")
    public ResponseEntity<?> rejectCompetition(@PathVariable Long competitionId, @RequestParam Long adminId, @RequestBody Map<String, String> data) {
        try {
            // Verify admin role
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (admin.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new RuntimeException("Competition not found"));

            competition.setStatus(ApprovalStatus.REJECTED);
            competition.setRejectionReason(data.get("reason"));
            competitionRepository.save(competition);

            return ResponseEntity.ok(Map.of("message", "Competition rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
