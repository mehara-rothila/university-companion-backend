package com.smartuniversity.repository;

import com.smartuniversity.model.CompetitionEnrollment;
import com.smartuniversity.model.CompetitionEnrollment.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionEnrollmentRepository extends JpaRepository<CompetitionEnrollment, Long> {

    // Find all enrollments for a competition
    List<CompetitionEnrollment> findByCompetitionIdAndStatusOrderByEnrolledAtAsc(Long competitionId, EnrollmentStatus status);

    // Find enrollment by competition and user
    Optional<CompetitionEnrollment> findByCompetitionIdAndUserId(Long competitionId, Long userId);

    // Count active enrollments for a competition
    Long countByCompetitionIdAndStatus(Long competitionId, EnrollmentStatus status);

    // Find all enrollments for a user
    List<CompetitionEnrollment> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status);

    // Check if user is enrolled in a competition
    boolean existsByCompetitionIdAndUserIdAndStatus(Long competitionId, Long userId, EnrollmentStatus status);
}
