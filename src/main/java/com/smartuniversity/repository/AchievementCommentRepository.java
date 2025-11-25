package com.smartuniversity.repository;

import com.smartuniversity.model.AchievementComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementCommentRepository extends JpaRepository<AchievementComment, Long> {

    // Find all comments for an achievement (not deleted)
    List<AchievementComment> findByAchievementIdAndIsDeletedOrderByCreatedAtDesc(Long achievementId, Boolean isDeleted);

    // Find all comments by a user
    List<AchievementComment> findByUserIdAndIsDeletedOrderByCreatedAtDesc(Long userId, Boolean isDeleted);

    // Count comments for an achievement (not deleted)
    Long countByAchievementIdAndIsDeleted(Long achievementId, Boolean isDeleted);
}
