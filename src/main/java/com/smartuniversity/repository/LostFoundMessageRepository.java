package com.smartuniversity.repository;

import com.smartuniversity.model.LostFoundConversation;
import com.smartuniversity.model.LostFoundMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LostFoundMessageRepository extends JpaRepository<LostFoundMessage, Long> {

    // Find messages by conversation (paginated, newest last for chat display)
    @Query("SELECT m FROM LostFoundMessage m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.conversation.id = :conversationId " +
           "ORDER BY m.sentAt ASC")
    List<LostFoundMessage> findByConversationId(@Param("conversationId") Long conversationId);

    // Find messages by conversation (paginated)
    @Query("SELECT m FROM LostFoundMessage m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.conversation.id = :conversationId " +
           "ORDER BY m.sentAt DESC")
    Page<LostFoundMessage> findByConversationIdPaginated(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    // Get last message of a conversation
    @Query("SELECT m FROM LostFoundMessage m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.conversation.id = :conversationId " +
           "ORDER BY m.sentAt DESC " +
           "LIMIT 1")
    LostFoundMessage findLastMessageByConversationId(@Param("conversationId") Long conversationId);

    // Count unread messages for a user in a conversation
    @Query("SELECT COUNT(m) FROM LostFoundMessage m " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.sender.id != :userId " +
           "AND m.isRead = false")
    Long countUnreadMessages(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId);

    // Count total unread messages for a user across all conversations
    @Query("SELECT COUNT(m) FROM LostFoundMessage m " +
           "WHERE (m.conversation.requester.id = :userId OR m.conversation.owner.id = :userId) " +
           "AND m.sender.id != :userId " +
           "AND m.isRead = false " +
           "AND m.conversation.status = 'APPROVED'")
    Long countTotalUnreadForUser(@Param("userId") Long userId);

    // Mark messages as read for a user in a conversation
    @Modifying
    @Query("UPDATE LostFoundMessage m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.sender.id != :userId " +
           "AND m.isRead = false")
    void markMessagesAsRead(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId);

    // Delete all messages in a conversation
    void deleteByConversation(LostFoundConversation conversation);
}
