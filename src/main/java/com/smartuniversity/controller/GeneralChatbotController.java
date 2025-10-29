package com.smartuniversity.controller;

import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.service.GeneralChatbotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/chatbot")
public class GeneralChatbotController {

    @Autowired
    private GeneralChatbotService chatbotService;

    /**
     * General chat endpoint that supports text, images, and PDFs
     *
     * Example request:
     * {
     *   "message": "What's in this document?",
     *   "userId": 123,
     *   "imageUrls": ["https://s3.amazonaws.com/bucket/image.jpg"],
     *   "pdfUrls": ["https://s3.amazonaws.com/bucket/document.pdf"]
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        try {
            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ChatbotResponse.error("Message cannot be empty"));
            }

            // Process chat with AI
            ChatbotResponse response = chatbotService.chat(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

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
