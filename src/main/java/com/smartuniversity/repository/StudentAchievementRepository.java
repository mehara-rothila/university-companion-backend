package com.smartuniversity.repository;

import com.smartuniversity.model.StudentAchievement;
import com.smartuniversity.model.StudentAchievement.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StudentAchievementRepository extends JpaRepository<StudentAchievement, Long> {

    // Find all approved achievements
    List<StudentAchievement> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    // Find approved achievements not hidden (for social feed)
    List<StudentAchievement> findByStatusAndHiddenOrderByCreatedAtDesc(ApprovalStatus status, Boolean hidden);

    // Find achievements by student
    List<StudentAchievement> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    // Find achievements by student and status
    List<StudentAchievement> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, ApprovalStatus status);

    // Find approved achievements by category
    List<StudentAchievement> findByStatusAndCategoryOrderByCreatedAtDesc(ApprovalStatus status, String category);

    // Find approved achievements by category (not hidden)
    List<StudentAchievement> findByStatusAndCategoryAndHiddenOrderByCreatedAtDesc(
        ApprovalStatus status, String category, Boolean hidden);

    // Find achievements by date range
    List<StudentAchievement> findByStatusAndAchievementDateBetweenOrderByAchievementDateDesc(
        ApprovalStatus status, LocalDateTime startDate, LocalDateTime endDate);

    // Find pending achievements (for admin)
    List<StudentAchievement> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    // Find recent approved achievements (for feed)
    List<StudentAchievement> findTop10ByStatusAndHiddenOrderByCreatedAtDesc(ApprovalStatus status, Boolean hidden);

    // Find popular achievements (by likes)
    List<StudentAchievement> findTop10ByStatusAndHiddenOrderByLikesDesc(ApprovalStatus status, Boolean hidden);
}
