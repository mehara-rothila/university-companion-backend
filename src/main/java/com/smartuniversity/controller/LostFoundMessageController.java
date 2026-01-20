package com.smartuniversity.controller;

import com.smartuniversity.dto.*;
import com.smartuniversity.model.*;
import com.smartuniversity.repository.*;
import com.smartuniversity.util.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/lost-found/messages")
public class LostFoundMessageController {

    @Autowired
    private LostFoundConversationRepository conversationRepository;

    @Autowired
    private LostFoundMessageRepository messageRepository;

    @Autowired
    private LostFoundItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserBlockRepository blockRepository;

    // ==================== CONVERSATION ENDPOINTS ====================

    /**
     * Request to start a conversation about an item
     * Creates a contact request that the item owner must approve
     */
    @PostMapping("/conversations/request")
    @Transactional
    public ResponseEntity<?> requestConversation(
            @Valid @RequestBody LostFoundConversationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User requester = authUtils.getUserFromAuthHeader(authHeader);
            if (requester == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Get the item
            Optional<LostFoundItem> itemOpt = itemRepository.findById(request.getItemId());
            if (itemOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Item not found"));
            }

            LostFoundItem item = itemOpt.get();

            // Check if item has an owner
            if (item.getPostedBy() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Item owner not found"));
            }

            // Can't contact your own item
            if (item.getPostedBy().getId().equals(requester.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot contact your own item"));
            }

            // Check if either user has blocked the other
            if (blockRepository.existsBlockBetweenUsers(requester.getId(), item.getPostedBy().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot contact this user"));
            }

            // Check if conversation already exists
            Optional<LostFoundConversation> existingConversation =
                conversationRepository.findExistingConversation(item.getId(), requester.getId());

            if (existingConversation.isPresent()) {
                LostFoundConversation conv = existingConversation.get();
                if (conv.getStatus() == LostFoundConversation.ConversationStatus.PENDING) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Contact request already pending"));
                }
                if (conv.getStatus() == LostFoundConversation.ConversationStatus.APPROVED) {
                    return ResponseEntity.ok(new LostFoundConversationResponse(conv));
                }
            }

            // Create new conversation request
            LostFoundConversation conversation = new LostFoundConversation(
                item,
                requester,
                item.getPostedBy(),
                request.getInitialMessage()
            );

            LostFoundConversation savedConversation = conversationRepository.save(conversation);

            // Send real-time notification to owner
            sendConversationNotification(savedConversation, "NEW_REQUEST");

            return ResponseEntity.ok(new LostFoundConversationResponse(savedConversation));
        } catch (Exception e) {
            System.err.println("Error creating conversation request: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create conversation request"));
        }
    }

    /**
     * Get all conversations for the current user
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getMyConversations(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            List<LostFoundConversation> conversations;

            if (status != null && !status.isEmpty()) {
                LostFoundConversation.ConversationStatus statusEnum =
                    LostFoundConversation.ConversationStatus.valueOf(status.toUpperCase());
                conversations = conversationRepository.findByUserIdAndStatus(user.getId(), statusEnum);
            } else {
                conversations = conversationRepository.findByUserId(user.getId());
            }

            List<LostFoundConversationResponse> response = conversations.stream()
                .map(conv -> {
                    LostFoundConversationResponse dto = new LostFoundConversationResponse(conv);

                    // Add last message and unread count
                    LostFoundMessage lastMessage = messageRepository.findLastMessageByConversationId(conv.getId());
                    if (lastMessage != null) {
                        dto.setLastMessage(new LostFoundConversationResponse.MessageSummary(
                            lastMessage.getContent(),
                            lastMessage.getSentAt(),
                            lastMessage.getSender() != null ? lastMessage.getSender().getId() : null,
                            lastMessage.isRead()
                        ));
                    }

                    Long unreadCount = messageRepository.countUnreadMessages(conv.getId(), user.getId());
                    dto.setUnreadCount(unreadCount);

                    return dto;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching conversations: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch conversations"));
        }
    }

    /**
     * Get pending contact requests (for item owners)
     */
    @GetMapping("/conversations/pending")
    public ResponseEntity<?> getPendingRequests(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            List<LostFoundConversation> pendingRequests =
                conversationRepository.findPendingRequestsForOwner(user.getId());

            List<LostFoundConversationResponse> response = pendingRequests.stream()
                .map(LostFoundConversationResponse::new)
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching pending requests: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch pending requests"));
        }
    }

    /**
     * Approve a contact request
     */
    @PostMapping("/conversations/{conversationId}/approve")
    @Transactional
    public ResponseEntity<?> approveConversation(
            @PathVariable Long conversationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt = conversationRepository.findByIdWithDetails(conversationId);
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only owner can approve
            if (!conversation.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the item owner can approve requests"));
            }

            // Only pending requests can be approved
            if (conversation.getStatus() != LostFoundConversation.ConversationStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only pending requests can be approved"));
            }

            // Approve the conversation
            conversation.setStatus(LostFoundConversation.ConversationStatus.APPROVED);
            conversation.setApprovedAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            // Create system message about approval
            LostFoundMessage systemMessage = LostFoundMessage.createSystemMessage(
                conversation,
                user,
                "Contact request approved. You can now chat about this item."
            );
            messageRepository.save(systemMessage);

            // If there was an initial message, create it as the first actual message
            if (conversation.getInitialMessage() != null && !conversation.getInitialMessage().isEmpty()) {
                LostFoundMessage initialMsg = new LostFoundMessage(
                    conversation,
                    conversation.getRequester(),
                    conversation.getInitialMessage()
                );
                messageRepository.save(initialMsg);
            }

            // Send real-time notification to requester
            sendConversationNotification(conversation, "APPROVED");

            return ResponseEntity.ok(new LostFoundConversationResponse(conversation));
        } catch (Exception e) {
            System.err.println("Error approving conversation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to approve conversation"));
        }
    }

    /**
     * Reject a contact request
     */
    @PostMapping("/conversations/{conversationId}/reject")
    public ResponseEntity<?> rejectConversation(
            @PathVariable Long conversationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt = conversationRepository.findByIdWithDetails(conversationId);
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only owner can reject
            if (!conversation.getOwner().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the item owner can reject requests"));
            }

            // Only pending requests can be rejected
            if (conversation.getStatus() != LostFoundConversation.ConversationStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only pending requests can be rejected"));
            }

            // Reject the conversation
            conversation.setStatus(LostFoundConversation.ConversationStatus.REJECTED);
            conversationRepository.save(conversation);

            // Send notification to requester
            sendConversationNotification(conversation, "REJECTED");

            return ResponseEntity.ok(new LostFoundConversationResponse(conversation));
        } catch (Exception e) {
            System.err.println("Error rejecting conversation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to reject conversation"));
        }
    }

    /**
     * Close a conversation
     */
    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<?> closeConversation(
            @PathVariable Long conversationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt = conversationRepository.findByIdWithDetails(conversationId);
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only participants can close
            if (!conversation.getOwner().getId().equals(user.getId()) &&
                !conversation.getRequester().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Only participants can close the conversation"));
            }

            conversation.setStatus(LostFoundConversation.ConversationStatus.CLOSED);
            conversationRepository.save(conversation);

            // Create system message
            LostFoundMessage systemMessage = LostFoundMessage.createSystemMessage(
                conversation,
                user,
                "Conversation closed by " + user.getUsername()
            );
            messageRepository.save(systemMessage);

            return ResponseEntity.ok(new LostFoundConversationResponse(conversation));
        } catch (Exception e) {
            System.err.println("Error closing conversation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to close conversation"));
        }
    }

    // ==================== MESSAGE ENDPOINTS ====================

    /**
     * Send a message in a conversation
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            @Valid @RequestBody LostFoundMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User sender = authUtils.getUserFromAuthHeader(authHeader);
            if (sender == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt =
                conversationRepository.findByIdWithDetails(request.getConversationId());
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only approved conversations allow messaging
            if (conversation.getStatus() != LostFoundConversation.ConversationStatus.APPROVED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Conversation is not active"));
            }

            // Only participants can send messages
            if (!conversation.getOwner().getId().equals(sender.getId()) &&
                !conversation.getRequester().getId().equals(sender.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not a participant of this conversation"));
            }

            // Check if either user has blocked the other
            Long otherUserId = conversation.getOwner().getId().equals(sender.getId())
                ? conversation.getRequester().getId()
                : conversation.getOwner().getId();
            if (blockRepository.existsBlockBetweenUsers(sender.getId(), otherUserId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot send messages. User is blocked."));
            }

            // Create and save the message
            LostFoundMessage message = new LostFoundMessage(conversation, sender, request.getContent());
            LostFoundMessage savedMessage = messageRepository.save(message);

            // Update conversation timestamp
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            // Send real-time message via WebSocket
            LostFoundMessageResponse messageResponse = new LostFoundMessageResponse(savedMessage);
            sendMessageNotification(conversation, messageResponse, sender.getId());

            return ResponseEntity.ok(messageResponse);
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to send message"));
        }
    }

    /**
     * Get messages for a conversation
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @Transactional
    public ResponseEntity<?> getMessages(
            @PathVariable Long conversationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt = conversationRepository.findByIdWithDetails(conversationId);
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only participants can view messages
            if (!conversation.getOwner().getId().equals(user.getId()) &&
                !conversation.getRequester().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not a participant of this conversation"));
            }

            // Get messages
            List<LostFoundMessage> messages = messageRepository.findByConversationId(conversationId);

            // Mark messages as read
            messageRepository.markMessagesAsRead(conversationId, user.getId());

            List<LostFoundMessageResponse> response = messages.stream()
                .map(LostFoundMessageResponse::new)
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error fetching messages: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch messages"));
        }
    }

    /**
     * Get unread message count for current user
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Long unreadCount = messageRepository.countTotalUnreadForUser(user.getId());
            Long pendingRequests = conversationRepository.countPendingRequestsForOwner(user.getId());

            Map<String, Long> counts = new HashMap<>();
            counts.put("unreadMessages", unreadCount);
            counts.put("pendingRequests", pendingRequests);
            counts.put("total", unreadCount + pendingRequests);

            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            System.err.println("Error fetching unread count: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch unread count"));
        }
    }

    // ==================== WEBSOCKET HELPERS ====================

    private void sendConversationNotification(LostFoundConversation conversation, String eventType) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "LOST_FOUND_CONVERSATION");
            notification.put("event", eventType);
            notification.put("conversationId", conversation.getId());
            notification.put("itemId", conversation.getItem().getId());
            notification.put("itemTitle", conversation.getItem().getTitle());
            notification.put("timestamp", LocalDateTime.now().toString());

            // Send to the appropriate user based on event type
            Long targetUserId;
            if ("NEW_REQUEST".equals(eventType)) {
                targetUserId = conversation.getOwner().getId();
                notification.put("requesterName", conversation.getRequester().getUsername());
            } else {
                targetUserId = conversation.getRequester().getId();
                notification.put("ownerName", conversation.getOwner().getUsername());
            }

            messagingTemplate.convertAndSend("/topic/lost-found/" + targetUserId, notification);
        } catch (Exception e) {
            System.err.println("Error sending conversation notification: " + e.getMessage());
        }
    }

    private void sendMessageNotification(LostFoundConversation conversation,
                                         LostFoundMessageResponse message,
                                         Long senderId) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "LOST_FOUND_MESSAGE");
            notification.put("conversationId", conversation.getId());
            notification.put("message", message);
            notification.put("timestamp", LocalDateTime.now().toString());

            // Send to both participants
            messagingTemplate.convertAndSend(
                "/topic/lost-found/conversation/" + conversation.getId(),
                notification
            );

            // Also send notification to the recipient
            Long recipientId = conversation.getOwner().getId().equals(senderId)
                ? conversation.getRequester().getId()
                : conversation.getOwner().getId();

            messagingTemplate.convertAndSend("/topic/lost-found/" + recipientId, notification);
        } catch (Exception e) {
            System.err.println("Error sending message notification: " + e.getMessage());
        }
    }

    // ==================== TYPING INDICATOR ENDPOINT ====================

    /**
     * Send typing indicator to other participant
     */
    @PostMapping("/conversations/{conversationId}/typing")
    public ResponseEntity<?> sendTypingIndicator(
            @PathVariable Long conversationId,
            @RequestParam boolean isTyping,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            Optional<LostFoundConversation> convOpt = conversationRepository.findByIdWithDetails(conversationId);
            if (convOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Conversation not found"));
            }

            LostFoundConversation conversation = convOpt.get();

            // Only participants can send typing indicators
            if (!conversation.getOwner().getId().equals(user.getId()) &&
                !conversation.getRequester().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Not a participant"));
            }

            // Send typing indicator via WebSocket
            Map<String, Object> typingNotification = new HashMap<>();
            typingNotification.put("type", "TYPING_INDICATOR");
            typingNotification.put("conversationId", conversationId);
            typingNotification.put("userId", user.getId());
            typingNotification.put("username", user.getUsername());
            typingNotification.put("isTyping", isTyping);
            typingNotification.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                "/topic/lost-found/conversation/" + conversationId,
                typingNotification
            );

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            System.err.println("Error sending typing indicator: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to send typing indicator"));
        }
    }

    // ==================== ONLINE STATUS ENDPOINT ====================

    /**
     * Update user's online status
     */
    @PostMapping("/online-status")
    public ResponseEntity<?> updateOnlineStatus(
            @RequestParam boolean isOnline,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }

            // Broadcast online status to all relevant conversations
            Map<String, Object> statusNotification = new HashMap<>();
            statusNotification.put("type", "ONLINE_STATUS");
            statusNotification.put("userId", user.getId());
            statusNotification.put("username", user.getUsername());
            statusNotification.put("isOnline", isOnline);
            statusNotification.put("lastSeen", LocalDateTime.now().toString());

            // Send to user's topic (other users can subscribe)
            messagingTemplate.convertAndSend("/topic/lost-found/user/" + user.getId() + "/status", statusNotification);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            System.err.println("Error updating online status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update online status"));
        }
    }
}
