package com.smartuniversity.util;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AuthUtils {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    /**
     * Extract user from JWT token in Authorization header
     * Falls back to User ID 1, then any user, then null
     *
     * @param authHeader Authorization header value (e.g., "Bearer jwt-token")
     * @return User object or null if not found
     */
    public User getUserFromAuthHeader(String authHeader) {
        User user = null;

        // Try to get user from JWT token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String jwt = authHeader.substring(7); // Remove "Bearer " prefix
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                if (username != null) {
                    user = userRepository.findByUsername(username).orElse(null);
                    if (user != null) {
                        System.out.println("Found user from JWT: " + username + " (ID: " + user.getId() + ")");
                        return user;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error extracting user from JWT: " + e.getMessage());
            }
        }

        // Fallback: Try to find user ID 1 first
        System.out.println("No user from JWT, trying fallback methods...");
        Optional<User> userOpt = userRepository.findById(1L);
        if (userOpt.isPresent()) {
            user = userOpt.get();
            System.out.println("Using user ID 1");
            return user;
        }

        // Fallback: Try to find any user
        List<User> users = userRepository.findAll();
        if (!users.isEmpty()) {
            user = users.get(0);
            System.out.println("Using first available user: " + user.getUsername() + " (ID: " + user.getId() + ")");
            return user;
        }

        // If still no user found, return null
        System.out.println("No user found, returning null");
        return null;
    }

    /**
     * Extract admin user from JWT token in Authorization header
     * Returns null if user is not found or not an admin
     *
     * @param authHeader Authorization header value (e.g., "Bearer jwt-token")
     * @return User object if admin, null otherwise
     */
    public User getAdminFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        try {
            String jwt = authHeader.substring(7);
            String username = jwtUtils.getUserNameFromJwtToken(jwt);

            if (username != null) {
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null && user.getRole() == User.UserRole.ADMIN) {
                    return user;
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting admin from JWT: " + e.getMessage());
        }

        return null;
    }

    /**
     * Check if the user from auth header is an admin
     *
     * @param authHeader Authorization header value
     * @return true if user is admin, false otherwise
     */
    public boolean isAdmin(String authHeader) {
        return getAdminFromAuthHeader(authHeader) != null;
    }
}
