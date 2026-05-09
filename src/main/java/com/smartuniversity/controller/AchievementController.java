package com.smartuniversity.controller;

import com.smartuniversity.model.StudentAchievement;
import com.smartuniversity.model.AchievementComment;
import com.smartuniversity.model.User;
import com.smartuniversity.service.AchievementService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.smartuniversity.exception.NotFoundException;
import com.smartuniversity.exception.UnauthorizedException;
import com.smartuniversity.exception.ForbiddenException;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private AuthUtils authUtils;

    // Create new achievement
    @PostMapping
    @Transactional
    public ResponseEntity<?> createAchievement(@RequestBody StudentAchievement achievement,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        // Security: prevent student ID impersonation.
        // Non-admins can only create achievements for themselves.
        // Admins may optionally pass a studentId to create on behalf of a student.
        if (!authUtils.isAdmin(authHeader) || achievement.getStudentId() == null) {
            achievement.setStudentId(user.getId());
        }
        StudentAchievement created = achievementService.createAchievement(achievement);
        return ResponseEntity.ok(Map.of(
            "message", "Achievement submitted successfully and is pending approval",
            "achievementId", created.getId()
        ));
    }

    // Get all approved achievements (social feed)
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedAchievements() {
        List<Map<String, Object>> achievements = achievementService.getApprovedAchievements();
        return ResponseEntity.ok(achievements);
    }

    // Get approved achievements by category
    @GetMapping("/approved/category/{category}")
    public ResponseEntity<?> getApprovedAchievementsByCategory(@PathVariable String category) {
        List<Map<String, Object>> achievements = achievementService.getApprovedAchievementsByCategory(category);
        return ResponseEntity.ok(achievements);
    }

    // Get achievements by student
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getAchievementsByStudent(@PathVariable Long studentId) {
        List<Map<String, Object>> achievements = achievementService.getAchievementsByStudent(studentId);
        return ResponseEntity.ok(achievements);
    }

    // Get achievement by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getAchievementById(@PathVariable Long id) {
        Optional<Map<String, Object>> achievement = achievementService.getAchievementById(id);
        if (achievement.isPresent()) {
            return ResponseEntity.ok(achievement.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Get popular achievements
    @GetMapping("/popular")
    public ResponseEntity<?> getPopularAchievements() {
        List<Map<String, Object>> achievements = achievementService.getPopularAchievements();
        return ResponseEntity.ok(achievements);
    }

    // Get recent achievements
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentAchievements() {
        List<Map<String, Object>> achievements = achievementService.getRecentAchievements();
        return ResponseEntity.ok(achievements);
    }

    // Admin: Get pending achievements
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingAchievements(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        List<Map<String, Object>> achievements = achievementService.getPendingAchievements();
        return ResponseEntity.ok(achievements);
    }

    // Admin: Approve achievement
    @PostMapping("/{achievementId}/approve")
    @Transactional
    public ResponseEntity<?> approveAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        User admin = authUtils.getAdminFromAuthHeader(authHeader);
        if (admin == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        achievementService.approveAchievement(achievementId, admin.getId());
        return ResponseEntity.ok(Map.of("message", "Achievement approved successfully"));
    }

    // Admin: Reject achievement
    @PostMapping("/{achievementId}/reject")
    @Transactional
    public ResponseEntity<?> rejectAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        // Verify admin role from JWT
        User admin = authUtils.getAdminFromAuthHeader(authHeader);
        if (admin == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        String reason = data.get("reason");
        achievementService.rejectAchievement(achievementId, admin.getId(), reason);
        return ResponseEntity.ok(Map.of("message", "Achievement rejected"));
    }

    // Admin: Hide achievement
    @PostMapping("/{achievementId}/hide")
    @Transactional
    public ResponseEntity<?> hideAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        achievementService.hideAchievement(achievementId);
        return ResponseEntity.ok(Map.of("message", "Achievement hidden successfully"));
    }

    // Admin: Unhide achievement
    @PostMapping("/{achievementId}/unhide")
    @Transactional
    public ResponseEntity<?> unhideAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        achievementService.unhideAchievement(achievementId);
        return ResponseEntity.ok(Map.of("message", "Achievement unhidden successfully"));
    }

    // Like achievement
    @PostMapping("/{achievementId}/like")
    @Transactional
    public ResponseEntity<?> likeAchievement(@PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        achievementService.likeAchievement(achievementId);
        return ResponseEntity.ok(Map.of("message", "Achievement liked"));
    }

    // Unlike achievement
    @PostMapping("/{achievementId}/unlike")
    @Transactional
    public ResponseEntity<?> unlikeAchievement(@PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        achievementService.unlikeAchievement(achievementId);
        return ResponseEntity.ok(Map.of("message", "Achievement unliked"));
    }

    // Share achievement
    @PostMapping("/{achievementId}/share")
    @Transactional
    public ResponseEntity<?> shareAchievement(@PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        achievementService.shareAchievement(achievementId);
        return ResponseEntity.ok(Map.of("message", "Achievement shared"));
    }

    // Add comment
    @PostMapping("/{achievementId}/comments")
    @Transactional
    public ResponseEntity<?> addComment(
        @PathVariable Long achievementId,
        @RequestBody Map<String, String> data,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String commentText = data.get("comment");
        if (commentText == null || commentText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment text is required"));
        }

        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        AchievementComment comment = achievementService.addComment(achievementId, user.getId(), commentText);
        return ResponseEntity.ok(Map.of(
            "message", "Comment added successfully",
            "commentId", comment.getId()
        ));
    }

    // Get comments for achievement
    @GetMapping("/{achievementId}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long achievementId) {
        List<Map<String, Object>> comments = achievementService.getAchievementComments(achievementId);
        return ResponseEntity.ok(comments);
    }

    // Delete comment
    @DeleteMapping("/comments/{commentId}")
    @Transactional
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        boolean isAdmin = authUtils.isAdmin(authHeader);
        achievementService.deleteComment(commentId, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    // Update achievement
    @PutMapping("/{achievementId}")
    @Transactional
    public ResponseEntity<?> updateAchievement(
        @PathVariable Long achievementId,
        @RequestBody StudentAchievement updatedData,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        StudentAchievement updated = achievementService.updateAchievement(achievementId, updatedData, currentUser.getId());
        return ResponseEntity.ok(Map.of(
            "message", "Achievement updated successfully",
            "achievement", updated
        ));
    }

    // Delete achievement
    @DeleteMapping("/{achievementId}")
    @Transactional
    public ResponseEntity<?> deleteAchievement(@PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User currentUser = authUtils.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        achievementService.deleteAchievement(achievementId, currentUser.getId());
        return ResponseEntity.ok(Map.of("message", "Achievement deleted successfully"));
    }

    // Admin: Update achievement image
    @PutMapping("/{achievementId}/image")
    @Transactional
    public ResponseEntity<?> updateAchievementImage(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        // Verify admin role from JWT
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
        }

        String imageUrl = data.get("imageUrl");
        achievementService.updateAchievementImage(achievementId, imageUrl);
        return ResponseEntity.ok(Map.of("message", "Achievement image updated successfully"));
    }
}
