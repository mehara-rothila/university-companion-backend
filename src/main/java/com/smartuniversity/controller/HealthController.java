package com.smartuniversity.controller;

import com.smartuniversity.repository.LostFoundItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class HealthController {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private LostFoundItemRepository lostFoundItemRepository;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("version", "1.0.1-jackson-configured");
        response.put("message", "Backend running with Jackson JSR310 support");
        
        // Test database connection
        try {
            Connection conn = dataSource.getConnection();
            response.put("database", "CONNECTED");
            response.put("databaseUrl", conn.getMetaData().getURL());
            conn.close();
            
            // Try to count items
            long count = lostFoundItemRepository.count();
            response.put("lostFoundItems", count);
        } catch (Exception e) {
            response.put("database", "ERROR");
            response.put("databaseError", e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
        }
        
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