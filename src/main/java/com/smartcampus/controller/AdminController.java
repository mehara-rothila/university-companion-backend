package com.smartcampus.controller;

import com.smartcampus.model.User;
import com.smartcampus.repository.UserRepository;
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
        List<Integer> userIdsInt = (List<Integer>) request.get("userIds");
        List<Long> userIds = userIdsInt.stream().map(Integer::longValue).toList();
        String action = (String) request.get("action");
        
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User IDs are required"));
        }
        
        try {
            List<User> users = userRepository.findAllById(userIds);
            
            switch (action) {
                case "enable":
                    users.forEach(user -> user.setEnabled(true));
                    break;
                case "disable":
                    users.forEach(user -> user.setEnabled(false));
                    break;
                case "delete":
                    userRepository.deleteAllById(userIds);
                    return ResponseEntity.ok(Map.of("message", "Users deleted successfully"));
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid action"));
            }
            
            userRepository.saveAll(users);
            return ResponseEntity.ok(Map.of("message", "Bulk action completed successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to perform bulk action: " + e.getMessage()));
        }
    }
    
}