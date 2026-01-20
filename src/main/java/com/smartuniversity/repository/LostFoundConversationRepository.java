package com.smartuniversity.repository;

import com.smartuniversity.model.LostFoundConversation;
import com.smartuniversity.model.LostFoundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LostFoundConversationRepository extends JpaRepository<LostFoundConversation, Long> {

    // Find all conversations for a user (both as requester and owner)
    @Query("SELECT c FROM LostFoundConversation c " +
           "LEFT JOIN FETCH c.item " +
           "LEFT JOIN FETCH c.requester " +
           "LEFT JOIN FETCH c.owner " +
           "WHERE c.requester.id = :userId OR c.owner.id = :userId " +
           "ORDER BY c.updatedAt DESC")
    List<LostFoundConversation> findByUserId(@Param("userId") Long userId);

    // Find conversations by status for a user
    @Query("SELECT c FROM LostFoundConversation c " +
           "LEFT JOIN FETCH c.item " +
           "LEFT JOIN FETCH c.requester " +
           "LEFT JOIN FETCH c.owner " +
           "WHERE (c.requester.id = :userId OR c.owner.id = :userId) " +
           "AND c.status = :status " +
           "ORDER BY c.updatedAt DESC")
    List<LostFoundConversation> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") LostFoundConversation.ConversationStatus status);

    // Find pending requests for an owner (requests waiting for their approval)
    @Query("SELECT c FROM LostFoundConversation c " +
           "LEFT JOIN FETCH c.item " +
           "LEFT JOIN FETCH c.requester " +
           "WHERE c.owner.id = :ownerId AND c.status = 'PENDING' " +
           "ORDER BY c.createdAt DESC")
    List<LostFoundConversation> findPendingRequestsForOwner(@Param("ownerId") Long ownerId);

    // Find active conversations for a user
    @Query("SELECT c FROM LostFoundConversation c " +
           "LEFT JOIN FETCH c.item " +
           "LEFT JOIN FETCH c.requester " +
           "LEFT JOIN FETCH c.owner " +
           "WHERE (c.requester.id = :userId OR c.owner.id = :userId) " +
           "AND c.status = 'APPROVED' " +
           "ORDER BY c.updatedAt DESC")
    List<LostFoundConversation> findActiveConversationsForUser(@Param("userId") Long userId);

    // Check if a conversation already exists between a requester and an item
    @Query("SELECT c FROM LostFoundConversation c " +
           "WHERE c.item.id = :itemId AND c.requester.id = :requesterId " +
           "AND c.status IN ('PENDING', 'APPROVED')")
    Optional<LostFoundConversation> findExistingConversation(
            @Param("itemId") Long itemId,
            @Param("requesterId") Long requesterId);

    // Find conversation with all details
    @Query("SELECT c FROM LostFoundConversation c " +
           "LEFT JOIN FETCH c.item " +
           "LEFT JOIN FETCH c.requester " +
           "LEFT JOIN FETCH c.owner " +
           "WHERE c.id = :id")
    Optional<LostFoundConversation> findByIdWithDetails(@Param("id") Long id);

    // Count pending requests for a user (as owner)
    @Query("SELECT COUNT(c) FROM LostFoundConversation c " +
           "WHERE c.owner.id = :ownerId AND c.status = 'PENDING'")
    Long countPendingRequestsForOwner(@Param("ownerId") Long ownerId);

    // Find all conversations for an item
    List<LostFoundConversation> findByItem(LostFoundItem item);
}
