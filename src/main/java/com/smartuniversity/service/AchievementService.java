package com.smartuniversity.service;

import com.smartuniversity.model.StudentAchievement;
import com.smartuniversity.model.StudentAchievement.ApprovalStatus;
import com.smartuniversity.model.AchievementComment;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.StudentAchievementRepository;
import com.smartuniversity.repository.AchievementCommentRepository;
import com.smartuniversity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import com.smartuniversity.exception.NotFoundException;
import com.smartuniversity.exception.UnauthorizedException;
import com.smartuniversity.exception.ForbiddenException;

@Service
public class AchievementService {

    @Autowired
    private StudentAchievementRepository achievementRepository;

    @Autowired
    private AchievementCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    // Create new achievement
    @Transactional
    public StudentAchievement createAchievement(StudentAchievement achievement) {
        achievement.setLikes(0);
        achievement.setComments(0);
        achievement.setShares(0);
        achievement.setHidden(false);

        // Faculty auto-approval: Faculty members are Verified Creators
        User creator = userRepository.findById(achievement.getStudentId()).orElse(null);
        if (creator != null && creator.getRole() == User.UserRole.FACULTY) {
            achievement.setStatus(ApprovalStatus.APPROVED);
            achievement.setApprovedAt(java.time.LocalDateTime.now());
            achievement.setApprovedBy(achievement.getStudentId()); // Self-approved as Verified Creator
        } else {
            achievement.setStatus(ApprovalStatus.PENDING);
        }

        return achievementRepository.save(achievement);
    }

    // Get all approved achievements (for social feed)
    public List<Map<String, Object>> getApprovedAchievements() {
        List<StudentAchievement> achievements = achievementRepository
            .findByStatusAndHiddenOrderByCreatedAtDesc(ApprovalStatus.APPROVED, false);
        return buildAchievementResponseList(achievements);
    }

    // Get approved achievements by category
    public List<Map<String, Object>> getApprovedAchievementsByCategory(String category) {
        List<StudentAchievement> achievements = achievementRepository
            .findByStatusAndCategoryAndHiddenOrderByCreatedAtDesc(ApprovalStatus.APPROVED, category, false);
        return buildAchievementResponseList(achievements);
    }

    // Get achievements by student
    public List<Map<String, Object>> getAchievementsByStudent(Long studentId) {
        List<StudentAchievement> achievements = achievementRepository
            .findByStudentIdOrderByCreatedAtDesc(studentId);
        return buildAchievementResponseList(achievements);
    }

    // Get pending achievements (for admin)
    public List<Map<String, Object>> getPendingAchievements() {
        List<StudentAchievement> achievements = achievementRepository
            .findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);
        return buildAchievementResponseList(achievements);
    }

    // Get achievement by ID
    public Optional<Map<String, Object>> getAchievementById(Long id) {
        Optional<StudentAchievement> achievement = achievementRepository.findById(id);
        if (achievement.isPresent()) {
            StudentAchievement a = achievement.get();
            Map<Long, User> userMap = new HashMap<>();
            userRepository.findById(a.getStudentId()).ifPresent(u -> userMap.put(u.getId(), u));
            Map<String, Object> response = buildAchievementResponse(a, userMap);
            // Include comments
            List<Map<String, Object>> comments = getAchievementComments(id);
            response.put("commentsList", comments);
            return Optional.of(response);
        }
        return Optional.empty();
    }

    // Approve achievement
    @Transactional
    public void approveAchievement(Long achievementId, Long adminId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));

        if (achievement.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending achievements can be approved");
        }

        achievement.setStatus(ApprovalStatus.APPROVED);
        achievement.setApprovedAt(LocalDateTime.now());
        achievement.setApprovedBy(adminId);
        achievementRepository.save(achievement);
    }

    // Reject achievement
    @Transactional
    public void rejectAchievement(Long achievementId, Long adminId, String reason) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));

        if (achievement.getStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending achievements can be rejected");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Rejection reason is required");
        }

        achievement.setStatus(ApprovalStatus.REJECTED);
        achievement.setRejectedAt(LocalDateTime.now());
        achievement.setRejectedBy(adminId);
        achievement.setRejectionReason(reason);
        achievementRepository.save(achievement);
    }

    // Hide achievement (soft delete)
    @Transactional
    public void hideAchievement(Long achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        achievement.setHidden(true);
        achievementRepository.save(achievement);
    }

    // Unhide achievement
    @Transactional
    public void unhideAchievement(Long achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        achievement.setHidden(false);
        achievementRepository.save(achievement);
    }

    // Update achievement image (admin only)
    @Transactional
    public void updateAchievementImage(Long achievementId, String imageUrl) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        achievement.setImageUrl(imageUrl);
        achievementRepository.save(achievement);
    }

    // Like achievement
    @Transactional
    public void likeAchievement(Long achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        achievement.setLikes(achievement.getLikes() + 1);
        achievementRepository.save(achievement);
    }

    // Unlike achievement
    @Transactional
    public void unlikeAchievement(Long achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        if (achievement.getLikes() > 0) {
            achievement.setLikes(achievement.getLikes() - 1);
        }
        achievementRepository.save(achievement);
    }

    // Share achievement
    @Transactional
    public void shareAchievement(Long achievementId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));
        achievement.setShares(achievement.getShares() + 1);
        achievementRepository.save(achievement);
    }

    // Add comment to achievement
    @Transactional
    public AchievementComment addComment(Long achievementId, Long userId, String commentText) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));

        AchievementComment comment = new AchievementComment(achievementId, userId, commentText);
        comment = commentRepository.save(comment);

        // Update comment count
        achievement.setComments(achievement.getComments() + 1);
        achievementRepository.save(achievement);

        return comment;
    }

    // Get comments for achievement
    public List<Map<String, Object>> getAchievementComments(Long achievementId) {
        List<AchievementComment> comments = commentRepository
            .findByAchievementIdAndIsDeletedOrderByCreatedAtDesc(achievementId, false);

        // Batch fetch users to avoid N+1 queries
        Set<Long> userIds = new HashSet<>();
        for (AchievementComment comment : comments) {
            userIds.add(comment.getUserId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userMap.put(u.getId(), u);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (AchievementComment comment : comments) {
            Map<String, Object> commentData = new HashMap<>();
            commentData.put("id", comment.getId());
            commentData.put("comment", comment.getComment());
            commentData.put("createdAt", comment.getCreatedAt());
            commentData.put("updatedAt", comment.getUpdatedAt());

            // Add user info
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                commentData.put("userName", user.getFirstName() + " " + user.getLastName());
                commentData.put("userImageUrl", user.getImageUrl());
                commentData.put("userRole", user.getRole().name());
            }

            response.add(commentData);
        }

        return response;
    }

    // Delete comment
    @Transactional
    public void deleteComment(Long commentId, Long userId, boolean isAdmin) {
        AchievementComment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userId) && !isAdmin) {
            throw new ForbiddenException("Unauthorized to delete this comment");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        // Update comment count
        StudentAchievement achievement = achievementRepository.findById(comment.getAchievementId())
            .orElse(null);
        if (achievement != null && achievement.getComments() > 0) {
            achievement.setComments(achievement.getComments() - 1);
            achievementRepository.save(achievement);
        }
    }

    // Update achievement
    @Transactional
    public StudentAchievement updateAchievement(Long achievementId, StudentAchievement updatedData, Long userId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));

        // Only the owner can update their achievement
        if (!achievement.getStudentId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this achievement");
        }

        // Update fields
        if (updatedData.getTitle() != null) {
            achievement.setTitle(updatedData.getTitle());
        }
        if (updatedData.getDescription() != null) {
            achievement.setDescription(updatedData.getDescription());
        }
        if (updatedData.getCategory() != null) {
            achievement.setCategory(updatedData.getCategory());
        }
        if (updatedData.getImageUrl() != null) {
            achievement.setImageUrl(updatedData.getImageUrl());
        }
        if (updatedData.getAchievementDate() != null) {
            achievement.setAchievementDate(updatedData.getAchievementDate());
        }

        // Check if user is Faculty for auto-approval
        User owner = userRepository.findById(userId).orElse(null);
        boolean isFaculty = owner != null && owner.getRole() == User.UserRole.FACULTY;

        // Reset to pending if it was approved or rejected (requires re-approval after edit)
        // Faculty members get auto-approved on resubmit
        if (achievement.getStatus() == ApprovalStatus.APPROVED) {
            if (isFaculty) {
                // Faculty keeps approved status
                achievement.setApprovedAt(LocalDateTime.now());
                achievement.setApprovedBy(userId);
            } else {
                achievement.setStatus(ApprovalStatus.PENDING);
                achievement.setApprovedAt(null);
                achievement.setApprovedBy(null);
            }
        } else if (achievement.getStatus() == ApprovalStatus.REJECTED) {
            if (isFaculty) {
                // Faculty auto-approved on resubmit
                achievement.setStatus(ApprovalStatus.APPROVED);
                achievement.setApprovedAt(LocalDateTime.now());
                achievement.setApprovedBy(userId);
            } else {
                achievement.setStatus(ApprovalStatus.PENDING);
            }
            // Clear rejection info
            achievement.setRejectedAt(null);
            achievement.setRejectedBy(null);
            achievement.setRejectionReason(null);
        }

        return achievementRepository.save(achievement);
    }

    // Delete achievement (student can delete their own pending/rejected achievements)
    @Transactional
    public void deleteAchievement(Long achievementId, Long userId) {
        StudentAchievement achievement = achievementRepository.findById(achievementId)
            .orElseThrow(() -> new NotFoundException("Achievement not found"));

        if (!achievement.getStudentId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to delete this achievement");
        }

        if (achievement.getStatus() == ApprovalStatus.APPROVED) {
            throw new ForbiddenException("Approved achievements cannot be deleted");
        }

        // Cascade delete related comments
        commentRepository.deleteByAchievementId(achievementId);

        achievementRepository.delete(achievement);
    }

    // Helper method to build achievement response with user info
    private Map<String, Object> buildAchievementResponse(StudentAchievement achievement, Map<Long, User> userMap) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", achievement.getId());
        response.put("title", achievement.getTitle());
        response.put("description", achievement.getDescription());
        response.put("category", achievement.getCategory());
        response.put("imageUrl", achievement.getImageUrl());
        response.put("achievementDate", achievement.getAchievementDate());
        response.put("status", achievement.getStatus());
        response.put("studentId", achievement.getStudentId());
        response.put("createdAt", achievement.getCreatedAt());
        response.put("updatedAt", achievement.getUpdatedAt());
        response.put("approvedAt", achievement.getApprovedAt());
        response.put("approvedBy", achievement.getApprovedBy());
        response.put("rejectedAt", achievement.getRejectedAt());
        response.put("rejectedBy", achievement.getRejectedBy());
        response.put("rejectionReason", achievement.getRejectionReason());
        response.put("hidden", achievement.isHidden());
        response.put("likes", achievement.getLikes());
        response.put("comments", achievement.getComments());
        response.put("shares", achievement.getShares());

        // Add student info
        User student = userMap.get(achievement.getStudentId());
        if (student != null) {
            response.put("studentName", student.getFirstName() + " " + student.getLastName());
            response.put("studentEmail", student.getEmail());
            response.put("studentImageUrl", student.getImageUrl());
            response.put("studentMajor", student.getMajor());
            response.put("studentYear", student.getYear());
        }

        return response;
    }

    private List<Map<String, Object>> buildAchievementResponseList(List<StudentAchievement> achievements) {
        // Batch fetch students to avoid N+1 queries
        Set<Long> studentIds = new HashSet<>();
        for (StudentAchievement achievement : achievements) {
            studentIds.add(achievement.getStudentId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User u : userRepository.findAllById(studentIds)) {
            userMap.put(u.getId(), u);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (StudentAchievement achievement : achievements) {
            response.add(buildAchievementResponse(achievement, userMap));
        }
        return response;
    }

    // Get popular achievements (for discover tab)
    public List<Map<String, Object>> getPopularAchievements() {
        List<StudentAchievement> achievements = achievementRepository
            .findTop10ByStatusAndHiddenOrderByLikesDesc(ApprovalStatus.APPROVED, false);
        return buildAchievementResponseList(achievements);
    }

    // Get recent achievements (for feed)
    public List<Map<String, Object>> getRecentAchievements() {
        List<StudentAchievement> achievements = achievementRepository
            .findTop10ByStatusAndHiddenOrderByCreatedAtDesc(ApprovalStatus.APPROVED, false);
        return buildAchievementResponseList(achievements);
    }
}
