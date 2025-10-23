package com.smartuniversity.repository;

import com.smartuniversity.model.Competition;
import com.smartuniversity.model.Competition.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    // Find all approved competitions
    List<Competition> findByStatusOrderByStartDateDesc(ApprovalStatus status);

    // Find all approved upcoming competitions (not yet ended)
    List<Competition> findByStatusAndEndDateAfterOrderByStartDateAsc(ApprovalStatus status, LocalDateTime now);

    // Find competitions by organizer
    List<Competition> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    // Find competitions by status and organizer
    List<Competition> findByOrganizerIdAndStatusOrderByCreatedAtDesc(Long organizerId, ApprovalStatus status);

    // Find pending competitions (for admin)
    List<Competition> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    // Find approved competitions by category
    List<Competition> findByStatusAndCategoryOrderByStartDateAsc(ApprovalStatus status, String category);
}
