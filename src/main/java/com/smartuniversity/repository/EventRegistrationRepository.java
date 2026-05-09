package com.smartuniversity.repository;

import com.smartuniversity.model.EventRegistration;
import com.smartuniversity.model.EventRegistration.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    // Find all registrations for an event by status
    List<EventRegistration> findByEventIdAndStatusOrderByRegisteredAtAsc(Long eventId, RegistrationStatus status);

    // Find specific user registration for an event
    Optional<EventRegistration> findByEventIdAndUserId(Long eventId, Long userId);

    // Count registrations by status
    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    // Find all user registrations
    List<EventRegistration> findByUserIdAndStatusOrderByRegisteredAtDesc(Long userId, RegistrationStatus status);

    // Check if user is registered for event
    boolean existsByEventIdAndUserIdAndStatus(Long eventId, Long userId, RegistrationStatus status);

    // Find all registrations for an event (any status)
    List<EventRegistration> findByEventIdOrderByRegisteredAtAsc(Long eventId);

    // Find waitlisted users
    List<EventRegistration> findByEventIdAndStatusOrderByMovedToWaitlistAtAsc(Long eventId, RegistrationStatus status);

    // Delete all registrations for an event (bulk DELETE, not N+1)
    @Modifying
    @Transactional
    @Query("DELETE FROM EventRegistration r WHERE r.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
