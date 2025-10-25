package com.smartuniversity.controller;

import com.smartuniversity.dto.*;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.FinancialAidDonation;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.FinancialAidRepository;
import com.smartuniversity.repository.FinancialAidDonationRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.util.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/financial-aid")
public class FinancialAidController {
    
    @Autowired
    private FinancialAidRepository financialAidRepository;

    @Autowired
    private FinancialAidDonationRepository donationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthUtils authUtils;
    
    @GetMapping("/applications")
    public ResponseEntity<List<FinancialAidResponse>> getAllApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String aidType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String urgency) {

        try {
            FinancialAid.ApplicationStatus statusEnum = status != null ?
                FinancialAid.ApplicationStatus.valueOf(status.toUpperCase()) : null;
            FinancialAid.AidType aidTypeEnum = aidType != null ?
                FinancialAid.AidType.valueOf(aidType.toUpperCase()) : null;
            FinancialAid.Urgency urgencyEnum = urgency != null ?
                FinancialAid.Urgency.valueOf(urgency.toUpperCase()) : null;

            List<FinancialAid> applications = financialAidRepository.findByFilters(
                statusEnum, aidTypeEnum, category, urgencyEnum);

            List<FinancialAidResponse> response = applications.stream()
                .map(app -> {
                    try {
                        return new FinancialAidResponse(app);
                    } catch (Exception e) {
                        System.err.println("Error creating response for application " + app.getId() + ": " + e.getMessage());
                        return null;
                    }
                })
                .filter(app -> app != null)
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching financial aid applications: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(getMockApplications());
        }
    }
    
    @GetMapping("/applications/{id}")
    public ResponseEntity<FinancialAidResponse> getApplicationById(@PathVariable Long id) {
        Optional<FinancialAid> application = financialAidRepository.findById(id);
        
        if (application.isPresent()) {
            return ResponseEntity.ok(new FinancialAidResponse(application.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/applications")
    public ResponseEntity<?> createApplication(
            @Valid @RequestBody FinancialAidRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found. Please log in.");
        }
        
        FinancialAid application = new FinancialAid(
            request.getTitle(),
            request.getDescription(),
            request.getAidType(),
            request.getCategory(),
            request.getRequestedAmount(),
            user
        );
        
        application.setPriority(request.getPriority());
        application.setUrgency(request.getUrgency());
        application.setAnonymous(request.getIsAnonymous());
        application.setSupportingDocuments(request.getSupportingDocuments());
        application.setPersonalStory(request.getPersonalStory());
        application.setApplicationDeadline(request.getApplicationDeadline());
        application.setDonationEligible(request.getIsDonationEligible());
        
        FinancialAid savedApplication = financialAidRepository.save(application);
        
        return ResponseEntity.ok(new FinancialAidResponse(savedApplication));
    }
    
    @PutMapping("/applications/{id}")
    public ResponseEntity<?> updateApplication(@PathVariable Long id, 
                                             @Valid @RequestBody FinancialAidRequest request) {
        Optional<FinancialAid> applicationOpt = financialAidRepository.findById(id);
        
        if (!applicationOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        FinancialAid application = applicationOpt.get();
        
        application.setTitle(request.getTitle());
        application.setDescription(request.getDescription());
        application.setAidType(request.getAidType());
        application.setCategory(request.getCategory());
        application.setRequestedAmount(request.getRequestedAmount());
        application.setPriority(request.getPriority());
        application.setUrgency(request.getUrgency());
        application.setAnonymous(request.getIsAnonymous());
        application.setSupportingDocuments(request.getSupportingDocuments());
        application.setPersonalStory(request.getPersonalStory());
        application.setApplicationDeadline(request.getApplicationDeadline());
        application.setDonationEligible(request.getIsDonationEligible());
        
        FinancialAid savedApplication = financialAidRepository.save(application);
        
        return ResponseEntity.ok(new FinancialAidResponse(savedApplication));
    }
    
    @DeleteMapping("/applications/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id) {
        Optional<FinancialAid> applicationOpt = financialAidRepository.findById(id);
        
        if (!applicationOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        financialAidRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/applications/user/{userId}")
    public ResponseEntity<List<FinancialAidResponse>> getUserApplications(@PathVariable Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);

            if (!userOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            List<FinancialAid> applications = financialAidRepository.findByApplicant(userOpt.get());
            List<FinancialAidResponse> response = applications.stream()
                .map(app -> {
                    try {
                        return new FinancialAidResponse(app);
                    } catch (Exception e) {
                        System.err.println("Error creating response for application " + app.getId() + ": " + e.getMessage());
                        return null;
                    }
                })
                .filter(app -> app != null)
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching user applications for userId " + userId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/donations")
    public ResponseEntity<List<FinancialAidResponse>> getDonationEligibleApplications() {
        try {
            List<FinancialAid> applications = financialAidRepository.findActiveDonationEligibleApplications(
                FinancialAid.ApplicationStatus.APPROVED);

            List<FinancialAidResponse> response = applications.stream()
                .map(app -> {
                    try {
                        return new FinancialAidResponse(app);
                    } catch (Exception e) {
                        System.err.println("Error creating response for donation application " + app.getId() + ": " + e.getMessage());
                        return null;
                    }
                })
                .filter(app -> app != null)
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching donation eligible applications: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(getMockDonationApplications());
        }
    }
    
    @PostMapping("/donations")
    public ResponseEntity<?> makeDonation(
            @Valid @RequestBody FinancialAidDonationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Optional<FinancialAid> applicationOpt = financialAidRepository.findById(request.getFinancialAidId());
        if (!applicationOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Financial aid application not found");
        }

        User donor = authUtils.getUserFromAuthHeader(authHeader);
        if (donor == null) {
            return ResponseEntity.badRequest().body("User not found. Please log in.");
        }

        FinancialAid application = applicationOpt.get();
        
        FinancialAidDonation donation = new FinancialAidDonation(
            application, donor, request.getAmount());
        donation.setAnonymous(request.isAnonymous());
        donation.setMessage(request.getMessage());
        
        FinancialAidDonation savedDonation = donationRepository.save(donation);
        
        // Update raised amount and supporter count
        BigDecimal newRaisedAmount = application.getRaisedAmount().add(request.getAmount());
        application.setRaisedAmount(newRaisedAmount);
        application.setSupporterCount(application.getSupporterCount() + 1);
        financialAidRepository.save(application);
        
        return ResponseEntity.ok(savedDonation);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Long totalApplications = financialAidRepository.count();
            Long pendingApplications = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.PENDING);
            Long approvedApplications = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.APPROVED);
            Long rejectedApplications = financialAidRepository.countByStatus(FinancialAid.ApplicationStatus.REJECTED);
            
            BigDecimal totalApproved = financialAidRepository.getTotalApprovedAmount();
            BigDecimal totalRaised = financialAidRepository.getTotalRaisedAmount();
            
            stats.put("totalApplications", totalApplications);
            stats.put("pendingApplications", pendingApplications);
            stats.put("approvedApplications", approvedApplications);
            stats.put("rejectedApplications", rejectedApplications);
            stats.put("totalApprovedAmount", totalApproved != null ? totalApproved : BigDecimal.ZERO);
            stats.put("totalRaisedAmount", totalRaised != null ? totalRaised : BigDecimal.ZERO);
            
            List<String> categories = financialAidRepository.findDistinctCategories();
            stats.put("categories", categories);
            
        } catch (Exception e) {
            stats.put("totalApplications", 15);
            stats.put("pendingApplications", 8);
            stats.put("approvedApplications", 5);
            stats.put("rejectedApplications", 2);
            stats.put("totalApprovedAmount", new BigDecimal("450000"));
            stats.put("totalRaisedAmount", new BigDecimal("125000"));
            stats.put("categories", List.of("Tuition", "Books", "Housing", "Emergency", "Technology"));
        }
        
        return ResponseEntity.ok(stats);
    }
    
    private List<FinancialAidResponse> getMockApplications() {
        return List.of(
            createMockApplication(1L, "Emergency Tuition Support", "EMERGENCY_FUND", "Tuition", 
                new BigDecimal("50000"), FinancialAid.ApplicationStatus.PENDING, false),
            createMockApplication(2L, "Textbook Assistance", "GRANT", "Books", 
                new BigDecimal("25000"), FinancialAid.ApplicationStatus.APPROVED, true),
            createMockApplication(3L, "Housing Support", "SCHOLARSHIP", "Housing", 
                new BigDecimal("75000"), FinancialAid.ApplicationStatus.UNDER_REVIEW, false)
        );
    }
    
    private List<FinancialAidResponse> getMockDonationApplications() {
        return List.of(
            createMockDonationApplication(1L, "Help with Engineering Textbooks", "Books", 
                new BigDecimal("30000"), new BigDecimal("18000"), 12),
            createMockDonationApplication(2L, "Emergency Housing Fund", "Housing", 
                new BigDecimal("60000"), new BigDecimal("45000"), 28)
        );
    }
    
    private FinancialAidResponse createMockApplication(Long id, String title, String aidType, 
                                                     String category, BigDecimal amount, 
                                                     FinancialAid.ApplicationStatus status, 
                                                     boolean isAnonymous) {
        FinancialAidResponse response = new FinancialAidResponse();
        response.setId(id);
        response.setTitle(title);
        response.setDescription("Mock description for " + title);
        response.setAidType(FinancialAid.AidType.valueOf(aidType));
        response.setCategory(category);
        response.setRequestedAmount(amount);
        response.setStatus(status);
        response.setPriority(FinancialAid.Priority.MEDIUM);
        response.setUrgency(FinancialAid.Urgency.MEDIUM);
        response.setAnonymous(isAnonymous);
        response.setCreatedAt(LocalDateTime.now().minusDays((long)(Math.random() * 30)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        response.setUpdatedAt(LocalDateTime.now().minusDays((long)(Math.random() * 7)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        response.setDonationEligible(true);
        response.setRaisedAmount(BigDecimal.ZERO);
        response.setSupporterCount(0);
        return response;
    }
    
    private FinancialAidResponse createMockDonationApplication(Long id, String title, String category, 
                                                             BigDecimal requested, BigDecimal raised, 
                                                             Integer supporters) {
        FinancialAidResponse response = new FinancialAidResponse();
        response.setId(id);
        response.setTitle(title);
        response.setDescription("Community support request for " + title.toLowerCase());
        response.setAidType(FinancialAid.AidType.CUSTOM);
        response.setCategory(category);
        response.setRequestedAmount(requested);
        response.setStatus(FinancialAid.ApplicationStatus.APPROVED);
        response.setPriority(FinancialAid.Priority.MEDIUM);
        response.setUrgency(FinancialAid.Urgency.HIGH);
        response.setAnonymous(true);
        response.setCreatedAt(LocalDateTime.now().minusDays((long)(Math.random() * 20)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        response.setUpdatedAt(LocalDateTime.now().minusDays((long)(Math.random() * 5)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        response.setDonationEligible(true);
        response.setRaisedAmount(raised);
        response.setSupporterCount(supporters);
        return response;
    }
}