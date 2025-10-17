package com.smartuniversity.controller;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/setup")
public class SetupController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Create initial admin user (for setup purposes)
     * Remove this endpoint in production or secure it properly
     */
    @PostMapping("/create-admin")
    public ResponseEntity<?> createInitialAdmin(@RequestBody Map<String, String> request) {
        // Check if admin already exists
        long adminCount = userRepository.countByRole(User.UserRole.ADMIN);
        if (adminCount > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Admin user already exists"));
        }
        
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        
        if (username == null || password == null || email == null || firstName == null || lastName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
        }
        
        // Check if username or email already exists
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }
        
        try {
            User adminUser = new User(firstName, lastName, email, username, passwordEncoder.encode(password));
            adminUser.setRole(User.UserRole.ADMIN);
            adminUser.setEnabled(true);
            
            userRepository.save(adminUser);
            
            return ResponseEntity.ok(Map.of(
                "message", "Admin user created successfully",
                "username", username,
                "email", email
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create admin user: " + e.getMessage()));
        }
    }
    
    /**
     * Check if setup is needed (no admin users exist)
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSetupStatus() {
        long adminCount = userRepository.countByRole(User.UserRole.ADMIN);
        boolean setupNeeded = adminCount == 0;
        
        return ResponseEntity.ok(Map.of(
            "setupNeeded", setupNeeded,
            "adminCount", adminCount,
            "totalUsers", userRepository.count()
        ));
    }
}