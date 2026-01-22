package com.smartuniversity.repository;

import com.smartuniversity.model.AuditLog;
import com.smartuniversity.model.AuditLog.ActionType;
import com.smartuniversity.model.AuditLog.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Find logs by user
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Find logs by action type
    Page<AuditLog> findByActionTypeOrderByCreatedAtDesc(ActionType actionType, Pageable pageable);

    // Find logs by entity type
    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(EntityType entityType, Pageable pageable);

    // Find logs for a specific entity
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(EntityType entityType, Long entityId);

    // Find logs within date range
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Find logs by user and date range
    Page<AuditLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable
    );

    // Complex query with multiple filters
    @Query("SELECT al FROM AuditLog al WHERE " +
           "(:userId IS NULL OR al.userId = :userId) AND " +
           "(:actionType IS NULL OR al.actionType = :actionType) AND " +
           "(:entityType IS NULL OR al.entityType = :entityType) AND " +
           "(:startDate IS NULL OR al.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR al.createdAt <= :endDate) " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findByFilters(
        @Param("userId") Long userId,
        @Param("actionType") ActionType actionType,
        @Param("entityType") EntityType entityType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    // Get recent activity
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    // Count logs by user
    long countByUserId(Long userId);

    // Count logs by action type
    long countByActionType(ActionType actionType);
}
