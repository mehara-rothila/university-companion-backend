package com.smartuniversity.util;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthUtils {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    /**
     * Extract user from JWT token in Authorization header.
     * Returns null if token is missing, invalid, or user not found.
     *
     * @param authHeader Authorization header value (e.g., "Bearer jwt-token")
     * @return User object or null if not authenticated
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

        // No valid JWT token — return null (unauthenticated)
        System.err.println("No valid JWT token provided, returning null");
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

    /**
     * Get the current authenticated user's ID from Spring Security context.
     * Returns null if no authenticated user is found.
     *
     * @return User ID of currently authenticated user, or null
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String)) {

                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String username = userDetails.getUsername();

                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    return userOpt.get().getId();
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting current user ID: " + e.getMessage());
        }

        // No authenticated user found — return null
        return null;
    }
}
