package com.smartuniversity.controller;

import com.smartuniversity.dto.BlockUserRequest;
import com.smartuniversity.dto.BlockedUserResponse;
import com.smartuniversity.dto.ReportUserRequest;
import com.smartuniversity.dto.UserReportResponse;
import com.smartuniversity.model.*;
import com.smartuniversity.repository.*;
import com.smartuniversity.util.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/users")
public class UserBlockController {

    @Autowired
    private UserBlockRepository blockRepository;

    @Autowired
    private UserReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LostFoundConversationRepository conversationRepository;

    @Autowired
    private AuthUtils authUtils;

    // ==================== BLOCK ENDPOINTS ====================

    /**
     * Block a user
     */
    @PostMapping("/block")
    @Transactional
    public ResponseEntity<?> blockUser(
            @Valid @RequestBody BlockUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User blocker = authUtils.getUserFromAuthHeader(authHeader);
            if (blocker == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Can't block yourself
            if (blocker.getId().equals(request.getUserId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot block yourself"));
            }

            // Check if user exists
            Optional<User> blockedOpt = userRepository.findById(request.getUserId());
            if (blockedOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User blocked = blockedOpt.get();

            // Check if already blocked
            if (blockRepository.existsByBlockerAndBlocked(blocker.getId(), blocked.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "User is already blocked"));
            }

            // Create block
            UserBlock block = new UserBlock(blocker, blocked, request.getReason());
            blockRepository.save(block);

            return ResponseEntity.ok(Map.of(
                "message", "User blocked successfully",
                "blockedUser", new BlockedUserResponse(block)
            ));
        } catch (Exception e) {
            System.err.println("Error blocking user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to block user"));
        }
    }

    /**
     * Unblock a user
     */
    @DeleteMapping("/block/{userId}")
    @Transactional
    public ResponseEntity<?> unblockUser(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User blocker = authUtils.getUserFromAuthHeader(authHeader);
            if (blocker == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Check if block exists
            Optional<UserBlock> blockOpt = blockRepository.findByBlockerAndBlocked(blocker.getId(), userId);
            if (blockOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Block not found"));
            }

            blockRepository.delete(blockOpt.get());

            return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
        } catch (Exception e) {
            System.err.println("Error unblocking user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to unblock user"));
        }
    }

    /**
     * Get list of blocked users
     */
    @GetMapping("/blocked")
    public ResponseEntity<?> getBlockedUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            List<UserBlock> blocks = blockRepository.findAllByBlocker(user.getId());
            List<BlockedUserResponse> blockedUsers = blocks.stream()
                .map(BlockedUserResponse::new)
                .collect(Collectors.toList());

            return ResponseEntity.ok(blockedUsers);
        } catch (Exception e) {
            System.err.println("Error getting blocked users: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get blocked users"));
        }
    }

    /**
     * Check if a user is blocked
     */
    @GetMapping("/block/check/{userId}")
    public ResponseEntity<?> checkBlockStatus(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            boolean isBlocked = blockRepository.existsByBlockerAndBlocked(user.getId(), userId);
            boolean hasBlockedMe = blockRepository.existsByBlockerAndBlocked(userId, user.getId());
            boolean anyBlock = blockRepository.existsBlockBetweenUsers(user.getId(), userId);

            return ResponseEntity.ok(Map.of(
                "isBlocked", isBlocked,
                "hasBlockedMe", hasBlockedMe,
                "anyBlock", anyBlock
            ));
        } catch (Exception e) {
            System.err.println("Error checking block status: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to check block status"));
        }
    }

    // ==================== REPORT ENDPOINTS ====================

    /**
     * Report a user
     */
    @PostMapping("/report")
    @Transactional
    public ResponseEntity<?> reportUser(
            @Valid @RequestBody ReportUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User reporter = authUtils.getUserFromAuthHeader(authHeader);
            if (reporter == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Can't report yourself
            if (reporter.getId().equals(request.getUserId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot report yourself"));
            }

            // Check if user exists
            Optional<User> reportedOpt = userRepository.findById(request.getUserId());
            if (reportedOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User reportedUser = reportedOpt.get();

            // Check if already has pending report
            if (reportRepository.existsPendingReport(reporter.getId(), reportedUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You already have a pending report for this user"));
            }

            // Parse reason enum
            UserReport.ReportReason reason;
            try {
                reason = UserReport.ReportReason.valueOf(request.getReason().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid report reason"));
            }

            // Create report
            UserReport report = new UserReport(reporter, reportedUser, reason, request.getDescription());

            // Add conversation if provided
            if (request.getConversationId() != null) {
                Optional<LostFoundConversation> convOpt = conversationRepository.findById(request.getConversationId());
                convOpt.ifPresent(report::setConversation);
            }

            reportRepository.save(report);

            return ResponseEntity.ok(Map.of(
                "message", "Report submitted successfully",
                "reportId", report.getId()
            ));
        } catch (Exception e) {
            System.err.println("Error reporting user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to submit report"));
        }
    }

    /**
     * Get report reasons (for frontend dropdown)
     */
    @GetMapping("/report/reasons")
    public ResponseEntity<?> getReportReasons() {
        return ResponseEntity.ok(UserReport.ReportReason.values());
    }

    // ==================== ADMIN REPORT ENDPOINTS ====================

    /**
     * Get all reports (Admin only)
     */
    @GetMapping("/admin/reports")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllReports(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User admin = authUtils.getUserFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Check admin role
            if (!User.UserRole.ADMIN.equals(admin.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            List<UserReport> reports;
            if (status != null && !status.isEmpty()) {
                try {
                    UserReport.ReportStatus reportStatus = UserReport.ReportStatus.valueOf(status.toUpperCase());
                    reports = reportRepository.findAllByStatus(reportStatus);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
                }
            } else {
                reports = reportRepository.findAllOrderByCreatedAtDesc();
            }

            List<UserReportResponse> responses = reports.stream()
                .map(UserReportResponse::new)
                .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            System.err.println("Error getting reports: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get reports"));
        }
    }

    /**
     * Get pending reports count (Admin only)
     */
    @GetMapping("/admin/reports/pending-count")
    public ResponseEntity<?> getPendingReportsCount(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User admin = authUtils.getUserFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            if (!User.UserRole.ADMIN.equals(admin.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Long count = reportRepository.countPendingReports();
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            System.err.println("Error getting pending reports count: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get count"));
        }
    }

    /**
     * Update report status (Admin only)
     */
    @PutMapping("/admin/reports/{reportId}")
    @Transactional
    public ResponseEntity<?> updateReportStatus(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User admin = authUtils.getUserFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            if (!User.UserRole.ADMIN.equals(admin.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Optional<UserReport> reportOpt = reportRepository.findById(reportId);
            if (reportOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
            }

            UserReport report = reportOpt.get();

            // Update status
            String newStatus = request.get("status");
            if (newStatus != null) {
                try {
                    report.setStatus(UserReport.ReportStatus.valueOf(newStatus.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
                }
            }

            // Update admin notes
            String adminNotes = request.get("adminNotes");
            if (adminNotes != null) {
                report.setAdminNotes(adminNotes);
            }

            // Set reviewer info
            report.setReviewedBy(admin);
            report.setReviewedAt(java.time.LocalDateTime.now());

            reportRepository.save(report);

            return ResponseEntity.ok(Map.of(
                "message", "Report updated successfully",
                "report", new UserReportResponse(report)
            ));
        } catch (Exception e) {
            System.err.println("Error updating report: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update report"));
        }
    }

    /**
     * Delete a report (Admin only)
     */
    @DeleteMapping("/admin/reports/{reportId}")
    @Transactional
    public ResponseEntity<?> deleteReport(
            @PathVariable Long reportId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User admin = authUtils.getUserFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            if (!User.UserRole.ADMIN.equals(admin.getRole())) {
                return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
            }

            Optional<UserReport> reportOpt = reportRepository.findById(reportId);
            if (reportOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Report not found"));
            }

            reportRepository.delete(reportOpt.get());

            return ResponseEntity.ok(Map.of("message", "Report deleted successfully"));
        } catch (Exception e) {
            System.err.println("Error deleting report: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete report"));
        }
    }
}
