package com.smartuniversity.controller;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Autowired
    UserRepository userRepository;

    @Autowired
    S3Service s3Service;

    @Autowired
    AuthUtils authUtils;
    
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(user);
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody User updatedUser, 
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        
        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setMajor(updatedUser.getMajor());
        user.setYear(updatedUser.getYear());
        user.setPreferences(updatedUser.getPreferences());
        
        userRepository.save(user);
        return ResponseEntity.ok("Profile updated successfully!");
    }

    @PostMapping("/profile/image")
    public ResponseEntity<?> updateProfileImage(@RequestParam("file") MultipartFile file, 
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed");
            }
            
            // Validate file size (max 5MB for profile images)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size cannot exceed 5MB");
            }

            // Delete old profile image if it exists and is from S3 (not OAuth provider)
            String oldImageUrl = user.getImageUrl();
            if (oldImageUrl != null && oldImageUrl.contains("amazonaws.com")) {
                try {
                    String fileName = s3Service.extractFileNameFromUrl(oldImageUrl);
                    if (fileName != null) {
                        s3Service.deleteFile(fileName);
                    }
                } catch (Exception e) {
                    // Log but don't fail if old image deletion fails
                    System.err.println("Failed to delete old profile image: " + e.getMessage());
                }
            }

            // Upload new profile image
            String imageUrl = s3Service.uploadFile(file, "profile-images");
            
            // Update user's profile image URL
            user.setImageUrl(imageUrl);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("imageUrl", imageUrl);
            response.put("message", "Profile image updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update profile image: " + e.getMessage());
        }
    }

    @DeleteMapping("/profile/image")
    public ResponseEntity<?> deleteProfileImage(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            
            String imageUrl = user.getImageUrl();
            if (imageUrl != null && imageUrl.contains("amazonaws.com")) {
                String fileName = s3Service.extractFileNameFromUrl(imageUrl);
                if (fileName != null) {
                    s3Service.deleteFile(fileName);
                }
            }
            
            user.setImageUrl(null);
            userRepository.save(user);
            
            return ResponseEntity.ok("Profile image deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete profile image: " + e.getMessage());
        }
    }
    
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully!");
    }
}