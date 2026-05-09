package com.smartuniversity.repository;

import com.smartuniversity.model.EventComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    // Find all comments for an event (excluding deleted)
    List<EventComment> findByEventIdAndIsDeletedOrderByCreatedAtDesc(Long eventId, Boolean isDeleted);

    // Find all comments by a user
    List<EventComment> findByUserIdAndIsDeletedOrderByCreatedAtDesc(Long userId, Boolean isDeleted);

    // Count comments for an event (excluding deleted)
    long countByEventIdAndIsDeleted(Long eventId, Boolean isDeleted);

    // Delete all comments for an event (bulk DELETE, not N+1)
    @Modifying
    @Transactional
    @Query("DELETE FROM EventComment c WHERE c.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
