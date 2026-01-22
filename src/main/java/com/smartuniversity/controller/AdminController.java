package com.smartuniversity.controller;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Get dashboard statistics
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalStudents = userRepository.countByRole(User.UserRole.STUDENT);
        long totalFaculty = userRepository.countByRole(User.UserRole.FACULTY);
        long totalAdmins = userRepository.countByRole(User.UserRole.ADMIN);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalStudents", totalStudents);
        stats.put("totalFaculty", totalFaculty);
        stats.put("totalAdmins", totalAdmins);
        stats.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get all users with pagination and filtering
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) User.UserRole role,
            @RequestParam(required = false) String search) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> users;
        
        if (role != null && search != null && !search.trim().isEmpty()) {
            users = userRepository.findByRoleAndUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                role, search, search, pageable);
        } else if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else if (search != null && !search.trim().isEmpty()) {
            users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("users", users.getContent());
        response.put("currentPage", users.getNumber());
        response.put("totalItems", users.getTotalElements());
        response.put("totalPages", users.getTotalPages());
        response.put("hasNext", users.hasNext());
        response.put("hasPrevious", users.hasPrevious());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user by ID
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Create a new user (Admin only)
     * Used for creating faculty, staff, or additional admin accounts
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {
        try {
            String firstName = (String) request.get("firstName");
            String lastName = (String) request.get("lastName");
            String email = (String) request.get("email");
            String username = (String) request.get("username");
            String password = (String) request.get("password");
            String roleStr = (String) request.get("role");
            String studentId = (String) request.get("studentId");
            String major = (String) request.get("major");
            Integer year = request.get("year") != null ? ((Number) request.get("year")).intValue() : null;
            
            // Validate required fields
            if (firstName == null || firstName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "First name is required"));
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Last name is required"));
            }
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
            }
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
            }
            
            // Check for existing username or email
            if (userRepository.existsByUsername(username)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken"));
            }
            if (userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use"));
            }
            
            // Parse role (default to STUDENT if not specified)
            User.UserRole role = User.UserRole.STUDENT;
            if (roleStr != null && !roleStr.trim().isEmpty()) {
                try {
                    role = User.UserRole.valueOf(roleStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Must be STUDENT, FACULTY, or ADMIN"));
                }
            }
            
            // Create user
            User user = new User(firstName, lastName, email, username, passwordEncoder.encode(password));
            user.setRole(role);
            user.setEnabled(true);
            user.setEmailVerified(true); // Admin-created accounts are pre-verified
            user.setProvider("local");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            
            // Optional fields
            if (studentId != null) user.setStudentId(studentId);
            if (major != null) user.setMajor(major);
            if (year != null) user.setYear(year);
            
            userRepository.save(user);
            
            return ResponseEntity.ok(Map.of(
                "message", "User created successfully",
                "user", Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "username", user.getUsername(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName(),
                    "role", user.getRole().name(),
                    "emailVerified", user.isEmailVerified(),
                    "enabled", user.isEnabled()
                )
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create user: " + e.getMessage()));
        }
    }
    
    /**
     * Update user details
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        Optional<User> existingUserOpt = userRepository.findById(id);
        if (!existingUserOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        User existingUser = existingUserOpt.get();
        
        // Update fields
        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setUsername(updatedUser.getUsername());
        existingUser.setRole(updatedUser.getRole());
        existingUser.setStudentId(updatedUser.getStudentId());
        existingUser.setMajor(updatedUser.getMajor());
        existingUser.setYear(updatedUser.getYear());
        existingUser.setEnabled(updatedUser.isEnabled());
        
        try {
            userRepository.save(existingUser);
            return ResponseEntity.ok(Map.of("message", "User updated successfully", "user", existingUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update user: " + e.getMessage()));
        }
    }
    
    /**
     * Delete user
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete user: " + e.getMessage()));
        }
    }
    
    /**
     * Enable/Disable user
     */
    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        user.setEnabled(!user.isEnabled());
        
        try {
            userRepository.save(user);
            String status = user.isEnabled() ? "enabled" : "disabled";
            return ResponseEntity.ok(Map.of(
                "message", "User " + status + " successfully",
                "user", user
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update user status: " + e.getMessage()));
        }
    }
    
    /**
     * Reset user password
     */
    @PatchMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
        }
        
        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        
        try {
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to reset password: " + e.getMessage()));
        }
    }
    
    /**
     * Bulk operations
     */
    @PostMapping("/users/bulk-action")
    public ResponseEntity<?> bulkUserAction(@RequestBody Map<String, Object> request) {
        try {
            Object userIdsObj = request.get("userIds");
            String action = (String) request.get("action");
            
            if (userIdsObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User IDs are required"));
            }
            
            if (action == null || action.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Action is required"));
            }
            
            // Handle the userIds - could be List<Integer> or List<Long> depending on JSON parsing
            List<Long> userIds;
            if (userIdsObj instanceof List<?>) {
                List<?> rawList = (List<?>) userIdsObj;
                userIds = rawList.stream()
                    .map(id -> {
                        if (id instanceof Integer) {
                            return ((Integer) id).longValue();
                        } else if (id instanceof Long) {
                            return (Long) id;
                        } else if (id instanceof Number) {
                            return ((Number) id).longValue();
                        }
                        throw new IllegalArgumentException("Invalid user ID type: " + id.getClass());
                    })
                    .toList();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "User IDs must be an array"));
            }
            
            if (userIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User IDs list cannot be empty"));
            }
            
            List<User> users = userRepository.findAllById(userIds);
            
            switch (action.toLowerCase()) {
                case "enable":
                    users.forEach(user -> user.setEnabled(true));
                    userRepository.saveAll(users);
                    break;
                case "disable":
                    users.forEach(user -> user.setEnabled(false));
                    userRepository.saveAll(users);
                    break;
                case "delete":
                    // Delete users one by one to handle foreign key constraints gracefully
                    int successCount = 0;
                    java.util.List<String> failedUsers = new java.util.ArrayList<>();
                    for (User user : users) {
                        try {
                            userRepository.delete(user);
                            userRepository.flush(); // Force immediate execution to catch constraint errors
                            successCount++;
                        } catch (Exception ex) {
                            failedUsers.add(user.getEmail() + " (has related data)");
                        }
                    }
                    if (failedUsers.isEmpty()) {
                        return ResponseEntity.ok(Map.of("message", "All users deleted successfully", "count", successCount));
                    } else if (successCount > 0) {
                        return ResponseEntity.ok(Map.of(
                            "message", successCount + " users deleted, " + failedUsers.size() + " failed (have related records)",
                            "count", successCount,
                            "failed", failedUsers
                        ));
                    } else {
                        return ResponseEntity.badRequest().body(Map.of(
                            "error", "Could not delete users - they have related records (achievements, applications, etc.)",
                            "failed", failedUsers
                        ));
                    }
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid action: " + action + ". Valid actions are: enable, disable, delete"));
            }
            
            return ResponseEntity.ok(Map.of("message", "Bulk " + action + " completed successfully", "count", users.size()));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to perform bulk action: " + e.getMessage()));
        }
    }
    
}