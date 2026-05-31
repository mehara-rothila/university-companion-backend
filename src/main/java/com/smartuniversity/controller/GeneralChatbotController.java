package com.smartuniversity.controller;

import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.exception.TokenExhaustedException;
import com.smartuniversity.service.KimiChatService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/chatbot")
public class GeneralChatbotController {

    @Autowired
    private KimiChatService chatbotService;

    @Autowired
    private AuthUtils authUtils;

    /**
     * General chat endpoint that supports text, images, and PDFs.
     * userId is derived from JWT — any client-supplied userId is ignored.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        try {
            Long userId = authUtils.getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401)
                    .body(ChatbotResponse.error("Authentication required"));
            }

            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ChatbotResponse.error("Message cannot be empty"));
            }

            // Override client-supplied userId with JWT-derived value
            request.setUserId(userId);

            // Process chat with AI
            ChatbotResponse response = chatbotService.chat(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (TokenExhaustedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ChatbotResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ChatbotResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chatbot service is running");
    }
}
