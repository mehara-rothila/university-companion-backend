package com.smartuniversity.repository;

import com.smartuniversity.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    @Query("SELECT n FROM Notification n WHERE n.isActive = true AND " +
           "(n.expiresAt IS NULL OR n.expiresAt > :now) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findActiveNotifications(@Param("now") LocalDateTime now, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.isActive = true AND " +
           "(n.expiresAt IS NULL OR n.expiresAt > :now) AND " +
           "(n.target = 'ALL_STUDENTS' OR " +
           "(n.target = 'SPECIFIC_USERS' AND :userId MEMBER OF n.targetUserIds)) " +
           "ORDER BY n.priority DESC, n.createdAt DESC")
    Page<Notification> findNotificationsForUser(@Param("userId") Long userId, 
                                               @Param("now") LocalDateTime now, 
                                               Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.isActive = true AND " +
           "(n.expiresAt IS NULL OR n.expiresAt > :now) AND " +
           "(n.target = 'ALL_STUDENTS' OR " +
           "(n.target = 'SPECIFIC_USERS' AND :userId MEMBER OF n.targetUserIds)) AND " +
           "n.type = :type " +
           "ORDER BY n.priority DESC, n.createdAt DESC")
    List<Notification> findNotificationsForUserByType(@Param("userId") Long userId, 
                                                     @Param("type") Notification.NotificationType type,
                                                     @Param("now") LocalDateTime now);
    
    @Query("SELECT n FROM Notification n WHERE n.createdBy.id = :adminId " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findNotificationsByAdmin(@Param("adminId") Long adminId, Pageable pageable);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.isActive = true AND " +
           "(n.expiresAt IS NULL OR n.expiresAt > :now) AND " +
           "(n.target = 'ALL_STUDENTS' OR " +
           "(n.target = 'SPECIFIC_USERS' AND :userId MEMBER OF n.targetUserIds))")
    Long countActiveNotificationsForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
    
    List<Notification> findByTypeAndIsActiveTrue(Notification.NotificationType type);
    
    List<Notification> findByPriorityAndIsActiveTrue(Notification.NotificationPriority priority);
}