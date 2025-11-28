package com.smartuniversity.repository;

import com.smartuniversity.model.EmergencyNotificationAcknowledgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyNotificationAcknowledgmentRepository extends JpaRepository<EmergencyNotificationAcknowledgment, Long> {

    Optional<EmergencyNotificationAcknowledgment> findByNotificationIdAndUserId(@Param("notificationId") Long notificationId,
                                                                                 @Param("userId") Long userId);

    List<EmergencyNotificationAcknowledgment> findByNotificationId(@Param("notificationId") Long notificationId);

    @Query("SELECT COUNT(e) FROM EmergencyNotificationAcknowledgment e WHERE e.notification.id = :notificationId AND e.dismissedAt IS NOT NULL")
    Long countDismissedByNotificationId(@Param("notificationId") Long notificationId);

    @Query("SELECT COUNT(e) FROM EmergencyNotificationAcknowledgment e WHERE e.notification.id = :notificationId AND e.hasSeen = true")
    Long countSeenByNotificationId(@Param("notificationId") Long notificationId);

    @Query("SELECT e FROM EmergencyNotificationAcknowledgment e WHERE e.notification.id = :notificationId AND e.dismissedAt IS NOT NULL")
    List<EmergencyNotificationAcknowledgment> findDismissedByNotificationId(@Param("notificationId") Long notificationId);
}
