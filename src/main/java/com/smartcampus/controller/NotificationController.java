package com.smartcampus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/send")
    public Map<String, String> sendNotification(@RequestBody Map<String, String> notification) {
        String message = notification.get("message");
        String userId = notification.get("userId");
        
        NotificationMessage notificationMessage = new NotificationMessage(
            message, 
            LocalDateTime.now().toString(),
            "info"
        );
        
        if (userId != null && !userId.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, notificationMessage);
        } else {
            messagingTemplate.convertAndSend("/topic/notifications", notificationMessage);
        }
        
        return Map.of("status", "sent", "message", message);
    }

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String message) throws Exception {
        Thread.sleep(1000);
        return "Hello, " + message + "!";
    }

    public static class NotificationMessage {
        private String message;
        private String timestamp;
        private String type;

        public NotificationMessage(String message, String timestamp, String type) {
            this.message = message;
            this.timestamp = timestamp;
            this.type = type;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}