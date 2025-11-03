package com.smartuniversity.repository;

import com.smartuniversity.model.Event;
import com.smartuniversity.model.Event.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Find all approved events
    List<Event> findByStatusOrderByEventDateDesc(ApprovalStatus status);

    // Find all approved upcoming events (not yet ended)
    List<Event> findByStatusAndEventDateAfterOrderByEventDateAsc(ApprovalStatus status, LocalDateTime now);

    // Find all approved past events
    List<Event> findByStatusAndEventDateBeforeOrderByEventDateDesc(ApprovalStatus status, LocalDateTime now);

    // Find events by creator
    List<Event> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    // Find events by status and creator
    List<Event> findByCreatorIdAndStatusOrderByCreatedAtDesc(Long creatorId, ApprovalStatus status);

    // Find pending events (for admin)
    List<Event> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    // Find approved events by category
    List<Event> findByStatusAndCategoryOrderByEventDateAsc(ApprovalStatus status, String category);

    // Find approved events by location
    List<Event> findByStatusAndLocationContainingIgnoreCaseOrderByEventDateAsc(ApprovalStatus status, String location);

    // Find approved events by category and date range
    List<Event> findByStatusAndCategoryAndEventDateBetweenOrderByEventDateAsc(
        ApprovalStatus status, String category, LocalDateTime startDate, LocalDateTime endDate);

    // Find all approved events not hidden
    List<Event> findByStatusAndHiddenOrderByEventDateAsc(ApprovalStatus status, Boolean hidden);
}
