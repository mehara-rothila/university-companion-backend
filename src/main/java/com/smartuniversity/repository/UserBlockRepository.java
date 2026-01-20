package com.smartuniversity.repository;

import com.smartuniversity.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    @Query("SELECT ub FROM UserBlock ub WHERE ub.blocker.id = :blockerId AND ub.blocked.id = :blockedId")
    Optional<UserBlock> findByBlockerAndBlocked(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query("SELECT ub FROM UserBlock ub WHERE ub.blocker.id = :userId")
    List<UserBlock> findAllByBlocker(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END FROM UserBlock ub " +
           "WHERE ub.blocker.id = :blockerId AND ub.blocked.id = :blockedId")
    boolean existsByBlockerAndBlocked(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query("SELECT CASE WHEN COUNT(ub) > 0 THEN true ELSE false END FROM UserBlock ub " +
           "WHERE (ub.blocker.id = :user1 AND ub.blocked.id = :user2) OR (ub.blocker.id = :user2 AND ub.blocked.id = :user1)")
    boolean existsBlockBetweenUsers(@Param("user1") Long user1, @Param("user2") Long user2);

    @Query("SELECT ub.blocked.id FROM UserBlock ub WHERE ub.blocker.id = :userId")
    List<Long> findBlockedUserIds(@Param("userId") Long userId);

    @Query("SELECT ub.blocker.id FROM UserBlock ub WHERE ub.blocked.id = :userId")
    List<Long> findUsersWhoBlockedMe(@Param("userId") Long userId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
