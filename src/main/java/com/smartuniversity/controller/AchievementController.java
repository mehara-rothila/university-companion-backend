package com.smartuniversity.controller;

import com.smartuniversity.model.StudentAchievement;
import com.smartuniversity.model.AchievementComment;
import com.smartuniversity.model.User;
import com.smartuniversity.service.AchievementService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/achievements")
@CrossOrigin(origins = "*")
public class AchievementController {

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private AuthUtils authUtils;

    // Create new achievement
    @PostMapping
    public ResponseEntity<?> createAchievement(@RequestBody StudentAchievement achievement) {
        try {
            StudentAchievement created = achievementService.createAchievement(achievement);
            return ResponseEntity.ok(Map.of(
                "message", "Achievement submitted successfully and is pending approval",
                "achievementId", created.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get all approved achievements (social feed)
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedAchievements() {
        try {
            List<Map<String, Object>> achievements = achievementService.getApprovedAchievements();
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get approved achievements by category
    @GetMapping("/approved/category/{category}")
    public ResponseEntity<?> getApprovedAchievementsByCategory(@PathVariable String category) {
        try {
            List<Map<String, Object>> achievements = achievementService.getApprovedAchievementsByCategory(category);
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get achievements by student
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getAchievementsByStudent(@PathVariable Long studentId) {
        try {
            List<Map<String, Object>> achievements = achievementService.getAchievementsByStudent(studentId);
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get achievement by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getAchievementById(@PathVariable Long id) {
        try {
            Optional<Map<String, Object>> achievement = achievementService.getAchievementById(id);
            if (achievement.isPresent()) {
                return ResponseEntity.ok(achievement.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get popular achievements
    @GetMapping("/popular")
    public ResponseEntity<?> getPopularAchievements() {
        try {
            List<Map<String, Object>> achievements = achievementService.getPopularAchievements();
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get recent achievements
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentAchievements() {
        try {
            List<Map<String, Object>> achievements = achievementService.getRecentAchievements();
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Get pending achievements
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingAchievements(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            List<Map<String, Object>> achievements = achievementService.getPendingAchievements();
            return ResponseEntity.ok(achievements);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Approve achievement
    @PostMapping("/{achievementId}/approve")
    public ResponseEntity<?> approveAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            User admin = authUtils.getAdminFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            achievementService.approveAchievement(achievementId, admin.getId());
            return ResponseEntity.ok(Map.of("message", "Achievement approved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Reject achievement
    @PostMapping("/{achievementId}/reject")
    public ResponseEntity<?> rejectAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        try {
            // Verify admin role from JWT
            User admin = authUtils.getAdminFromAuthHeader(authHeader);
            if (admin == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            String reason = data.get("reason");
            achievementService.rejectAchievement(achievementId, admin.getId(), reason);
            return ResponseEntity.ok(Map.of("message", "Achievement rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Hide achievement
    @PostMapping("/{achievementId}/hide")
    public ResponseEntity<?> hideAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            achievementService.hideAchievement(achievementId);
            return ResponseEntity.ok(Map.of("message", "Achievement hidden successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Unhide achievement
    @PostMapping("/{achievementId}/unhide")
    public ResponseEntity<?> unhideAchievement(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            achievementService.unhideAchievement(achievementId);
            return ResponseEntity.ok(Map.of("message", "Achievement unhidden successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Like achievement
    @PostMapping("/{achievementId}/like")
    public ResponseEntity<?> likeAchievement(@PathVariable Long achievementId) {
        try {
            achievementService.likeAchievement(achievementId);
            return ResponseEntity.ok(Map.of("message", "Achievement liked"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Unlike achievement
    @PostMapping("/{achievementId}/unlike")
    public ResponseEntity<?> unlikeAchievement(@PathVariable Long achievementId) {
        try {
            achievementService.unlikeAchievement(achievementId);
            return ResponseEntity.ok(Map.of("message", "Achievement unliked"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Share achievement
    @PostMapping("/{achievementId}/share")
    public ResponseEntity<?> shareAchievement(@PathVariable Long achievementId) {
        try {
            achievementService.shareAchievement(achievementId);
            return ResponseEntity.ok(Map.of("message", "Achievement shared"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Add comment
    @PostMapping("/{achievementId}/comments")
    public ResponseEntity<?> addComment(
        @PathVariable Long achievementId,
        @RequestParam Long userId,
        @RequestBody Map<String, String> data
    ) {
        try {
            String commentText = data.get("comment");
            if (commentText == null || commentText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comment text is required"));
            }

            AchievementComment comment = achievementService.addComment(achievementId, userId, commentText);
            return ResponseEntity.ok(Map.of(
                "message", "Comment added successfully",
                "commentId", comment.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get comments for achievement
    @GetMapping("/{achievementId}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long achievementId) {
        try {
            List<Map<String, Object>> comments = achievementService.getAchievementComments(achievementId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete comment
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, @RequestParam Long userId) {
        try {
            achievementService.deleteComment(commentId, userId);
            return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Update achievement
    @PutMapping("/{achievementId}")
    public ResponseEntity<?> updateAchievement(
        @PathVariable Long achievementId,
        @RequestParam Long userId,
        @RequestBody StudentAchievement updatedData
    ) {
        try {
            StudentAchievement updated = achievementService.updateAchievement(achievementId, updatedData, userId);
            return ResponseEntity.ok(Map.of(
                "message", "Achievement updated successfully",
                "achievement", updated
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete achievement
    @DeleteMapping("/{achievementId}")
    public ResponseEntity<?> deleteAchievement(@PathVariable Long achievementId, @RequestParam Long userId) {
        try {
            achievementService.deleteAchievement(achievementId, userId);
            return ResponseEntity.ok(Map.of("message", "Achievement deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Admin: Update achievement image
    @PutMapping("/{achievementId}/image")
    public ResponseEntity<?> updateAchievementImage(
            @PathVariable Long achievementId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        try {
            // Verify admin role from JWT
            if (!authUtils.isAdmin(authHeader)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized - Admin access required"));
            }

            String imageUrl = data.get("imageUrl");
            achievementService.updateAchievementImage(achievementId, imageUrl);
            return ResponseEntity.ok(Map.of("message", "Achievement image updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
