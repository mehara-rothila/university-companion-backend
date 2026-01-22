package com.smartuniversity.repository;

import com.smartuniversity.model.EventAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventAttendanceRepository extends JpaRepository<EventAttendance, Long> {

    // Find attendance by event and user
    Optional<EventAttendance> findByEventIdAndUserId(Long eventId, Long userId);

    // Find all attendance for an event
    List<EventAttendance> findByEventId(Long eventId);

    // Find all attendance for a user
    List<EventAttendance> findByUserId(Long userId);

    // Check if user attended an event
    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    // Count attendance for an event
    long countByEventId(Long eventId);

    // Get attendance statistics for an event
    @Query("SELECT COUNT(ea) FROM EventAttendance ea WHERE ea.event.id = :eventId")
    long getAttendanceCount(@Param("eventId") Long eventId);

    // Delete attendance by event and user
    void deleteByEventIdAndUserId(Long eventId, Long userId);
}
