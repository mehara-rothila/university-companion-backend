package com.smartuniversity.repository;

import com.smartuniversity.model.EventComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    // Find all comments for an event (excluding deleted)
    List<EventComment> findByEventIdAndIsDeletedOrderByCreatedAtDesc(Long eventId, Boolean isDeleted);

    // Find all comments by a user
    List<EventComment> findByUserIdAndIsDeletedOrderByCreatedAtDesc(Long userId, Boolean isDeleted);

    // Count comments for an event (excluding deleted)
    long countByEventIdAndIsDeleted(Long eventId, Boolean isDeleted);
}
