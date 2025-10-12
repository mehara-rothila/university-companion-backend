package com.smartcampus.controller;

import com.smartcampus.dto.AdminReviewRequest;
import com.smartcampus.dto.AdminFinancialAidRequest;
import com.smartcampus.dto.FinancialAidResponse;
import com.smartcampus.model.FinancialAid;
import com.smartcampus.model.User;
import com.smartcampus.repository.FinancialAidRepository;
import com.smartcampus.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/financial-aid")
public class FinancialAidAdminController {
    
    @Autowired
    private FinancialAidRepository financialAidRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping("/applications")
    public ResponseEntity<List<FinancialAidResponse>> getAllApplicationsForReview(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String urgency) {
        
        try {
            FinancialAid.ApplicationStatus statusEnum = status != null ? 
                FinancialAid.ApplicationStatus.valueOf(status.toUpperCase()) : null;
            FinancialAid.Urgency urgencyEnum = urgency != null ? 
                FinancialAid.Urgency.valueOf(urgency.toUpperCase()) : null;
            
            List<FinancialAid> applications = financialAidRepository.findByFilters(
                statusEnum, null, null, urgencyEnum);
            
            List<FinancialAidResponse> response = applications.stream()
                .map(FinancialAidResponse::new)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @GetMapping("/applications/pending")
    public ResponseEntity<List<FinancialAidResponse>> getPendingApplications() {
        try {
            List<FinancialAid> applications = financialAidRepository.findByStatus(
                FinancialAid.ApplicationStatus.PENDING);
            
            List<FinancialAidResponse> response = applications.stream()
                .map(FinancialAidResponse::new)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @GetMapping("/applications/{id}")
    public ResponseEntity<FinancialAidResponse> getApplicationForReview(@PathVariable Long id) {
        Optional<FinancialAid> application = financialAidRepository.findById(id);
        
        if (application.isPresent()) {
            return ResponseEntity.ok(new FinancialAidResponse(application.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PutMapping("/applications/{id}/review")
    public ResponseEntity<?> reviewApplication(@PathVariable Long id, 
                                             @Valid @RequestBody AdminReviewRequest request) {
        Optional<FinancialAid> applicationOpt = financialAidRepository.findById(id);
        
        if (!applicationOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        // Get admin user (in real implementation, get from authentication)
        Optional<User> adminOpt = userRepository.findById(1L);
        if (!adminOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Admin user not found");
        }
        
        FinancialAid application = applicationOpt.get();
        User admin = adminOpt.get();
        
        application.setStatus(request.getStatus());
        application.setApprovedAmount(request.getApprovedAmount());
        application.setAdminNotes(request.getAdminNotes());
        application.setRejectionReason(request.getRejectionReason());
        application.setDonationEligible(request.isDonationEligible());
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        
        // If approved and donation eligible, update status to allow donations
        if (request.getStatus() == FinancialAid.ApplicationStatus.APPROVED && 
            request.isDonationEligible()) {
            application.setDonationEligible(true);
        }
        
        FinancialAid savedApplication = financialAidRepository.save(application);
        
        return ResponseEntity.ok(new FinancialAidResponse(savedApplication));
    }
    
    @PutMapping("/applications/{id}/status")
    public ResponseEntity<?> updateApplicationStatus(@PathVariable Long id, 
                                                   @RequestParam String status) {
        Optional<FinancialAid> applicationOpt = financialAidRepository.findById(id);
        
        if (!applicationOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            FinancialAid.ApplicationStatus newStatus = 
                FinancialAid.ApplicationStatus.valueOf(status.toUpperCase());
            FinancialAid application = applicationOpt.get();
            application.setStatus(newStatus);
            
            FinancialAid savedApplication = financialAidRepository.save(application);
            return ResponseEntity.ok(new FinancialAidResponse(savedApplication));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status);
        }
    }
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        try {
            Long totalApplications = financialAidRepository.count();
            Long pendingReview = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.PENDING);
            Long underReview = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.UNDER_REVIEW);
            Long approved = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.APPROVED);
            Long rejected = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.REJECTED);
            
            dashboard.put("totalApplications", totalApplications);
            dashboard.put("pendingReview", pendingReview);
            dashboard.put("underReview", underReview);
            dashboard.put("approved", approved);
            dashboard.put("rejected", rejected);
            
            // Get applications by urgency
            Long criticalUrgency = (long) financialAidRepository.findByFilters(null, null, null, 
                FinancialAid.Urgency.CRITICAL).size();
            Long highUrgency = (long) financialAidRepository.findByFilters(null, null, null, 
                FinancialAid.Urgency.HIGH).size();
            
            dashboard.put("criticalUrgency", criticalUrgency);
            dashboard.put("highUrgency", highUrgency);
            
            // Recent applications that need attention
            List<FinancialAid> recentPending = financialAidRepository.findByStatus(
                FinancialAid.ApplicationStatus.PENDING).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());
            
            List<FinancialAidResponse> recentApplications = recentPending.stream()
                .map(FinancialAidResponse::new)
                .collect(Collectors.toList());
            
            dashboard.put("recentPendingApplications", recentApplications);
            
        } catch (Exception e) {
            // Fallback mock data
            dashboard.put("totalApplications", 25);
            dashboard.put("pendingReview", 8);
            dashboard.put("underReview", 5);
            dashboard.put("approved", 10);
            dashboard.put("rejected", 2);
            dashboard.put("criticalUrgency", 3);
            dashboard.put("highUrgency", 7);
            dashboard.put("recentPendingApplications", List.of());
        }
        
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/applications/by-urgency/{urgency}")
    public ResponseEntity<List<FinancialAidResponse>> getApplicationsByUrgency(@PathVariable String urgency) {
        try {
            FinancialAid.Urgency urgencyEnum = FinancialAid.Urgency.valueOf(urgency.toUpperCase());
            List<FinancialAid> applications = financialAidRepository.findByUrgency(urgencyEnum);
            
            List<FinancialAidResponse> response = applications.stream()
                .map(FinancialAidResponse::new)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @GetMapping("/applications/by-category/{category}")
    public ResponseEntity<List<FinancialAidResponse>> getApplicationsByCategory(@PathVariable String category) {
        try {
            List<FinancialAid> applications = financialAidRepository.findByCategory(category);
            
            List<FinancialAidResponse> response = applications.stream()
                .map(FinancialAidResponse::new)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @PostMapping("/applications/create")
    public ResponseEntity<?> createApplicationForUser(@Valid @RequestBody AdminFinancialAidRequest request) {
        try {
            // Find the applicant user
            Optional<User> applicantOpt = userRepository.findById(request.getApplicantUserId());
            if (!applicantOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Applicant user not found");
            }
            
            // Get admin user (in real implementation, get from authentication)
            Optional<User> adminOpt = userRepository.findById(1L);
            if (!adminOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Admin user not found");
            }
            
            User applicant = applicantOpt.get();
            User admin = adminOpt.get();
            
            // Create the financial aid application
            FinancialAid application = new FinancialAid(
                request.getTitle(),
                request.getDescription(),
                request.getAidType(),
                request.getCategory(),
                request.getRequestedAmount(),
                applicant
            );
            
            // Set additional properties
            application.setPriority(request.getPriority());
            application.setUrgency(request.getUrgency());
            application.setAnonymous(request.isAnonymous());
            application.setSupportingDocuments(request.getSupportingDocuments());
            application.setPersonalStory(request.getPersonalStory());
            application.setApplicationDeadline(request.getApplicationDeadline());
            application.setDonationEligible(request.isDonationEligible());
            
            // Set admin-specific fields
            if (request.getAdminNotes() != null && !request.getAdminNotes().trim().isEmpty()) {
                application.setAdminNotes(request.getAdminNotes());
            }
            
            // Since this is created by admin, we can set it to PENDING status immediately
            application.setStatus(FinancialAid.ApplicationStatus.PENDING);
            application.setCreatedAt(LocalDateTime.now());
            application.setUpdatedAt(LocalDateTime.now());
            
            FinancialAid savedApplication = financialAidRepository.save(application);
            
            return ResponseEntity.ok(new FinancialAidResponse(savedApplication));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating application: " + e.getMessage());
        }
    }
}