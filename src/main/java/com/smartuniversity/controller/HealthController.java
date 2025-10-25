package com.smartuniversity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class HealthController {
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("version", "1.0.1-jackson-configured");
        response.put("message", "Backend running with Jackson JSR310 support");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/api/status")
    public String status() {
        return "OK";
    }
    
    @GetMapping("/api/datetime-test")
    public ResponseEntity<Map<String, Object>> dateTimeTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("currentTime", LocalDateTime.now());
        response.put("pastTime", LocalDateTime.now().minusDays(7));
        response.put("futureTime", LocalDateTime.now().plusDays(7));
        response.put("jacksonConfigured", true);
        return ResponseEntity.ok(response);
    }
}