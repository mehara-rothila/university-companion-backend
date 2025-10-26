package com.smartuniversity.controller;

import com.smartuniversity.dto.JwtResponse;
import com.smartuniversity.dto.LoginRequest;
import com.smartuniversity.dto.SignupRequest;
import com.smartuniversity.dto.OAuthUserRequest;
import com.smartuniversity.dto.ForgotPasswordRequest;
import com.smartuniversity.dto.ResetPasswordRequest;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import com.smartuniversity.service.UserPrincipal;
import com.smartuniversity.service.EmailService;
import com.smartuniversity.model.User.UserRole;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    AuthenticationManager authenticationManager;
    
    @Autowired
    UserRepository userRepository;
    
    @Autowired
    PasswordEncoder encoder;
    
    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EmailService emailService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        System.out.println("=== LOGIN REQUEST START ===");
        System.out.println("Raw request received");

        if (loginRequest == null) {
            System.out.println("LoginRequest is null!");
            return ResponseEntity.badRequest().body("No login data provided");
        }

        String usernameOrEmail = loginRequest.getUsernameOrEmail();
        System.out.println("Login request received for: " + usernameOrEmail);
        System.out.println("Password length: " + (loginRequest.getPassword() != null ? loginRequest.getPassword().length() : "null"));

        // Try to find user by username or email
        User user = null;

        // Check if input contains @ symbol to determine if it's likely an email
        if (usernameOrEmail.contains("@")) {
            System.out.println("Attempting to find user by email: " + usernameOrEmail);
            user = userRepository.findByEmail(usernameOrEmail).orElse(null);
        } else {
            System.out.println("Attempting to find user by username: " + usernameOrEmail);
            user = userRepository.findByUsername(usernameOrEmail).orElse(null);
        }

        // If not found by the first method, try the other method as fallback
        if (user == null) {
            System.out.println("User not found by primary method, trying alternative...");
            if (usernameOrEmail.contains("@")) {
                user = userRepository.findByUsername(usernameOrEmail).orElse(null);
            } else {
                user = userRepository.findByEmail(usernameOrEmail).orElse(null);
            }
        }

        if (user == null) {
            System.out.println("User not found for: " + usernameOrEmail);
            return ResponseEntity.badRequest().body("Error: Invalid username/email or password!");
        }

        System.out.println("User found: " + user.getUsername() + " (email: " + user.getEmail() + "), returning JWT response");
        
        // Generate real JWT token
        String jwt = jwtUtils.generateJwtToken(user.getUsername());

        JwtResponse response = new JwtResponse(jwt,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole());

        System.out.println("Sending response for user: " + user.getUsername());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body("Error: Username is already taken!");
        }
        
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body("Error: Email is already in use!");
        }
        
        User user = new User(signUpRequest.getFirstName(),
                            signUpRequest.getLastName(),
                            signUpRequest.getEmail(),
                            signUpRequest.getUsername(),
                            encoder.encode(signUpRequest.getPassword()));
        
        user.setStudentId(signUpRequest.getStudentId());
        user.setMajor(signUpRequest.getMajor());
        user.setYear(signUpRequest.getYear());
        user.setRole(signUpRequest.getRole());
        if (signUpRequest.getPreferences() != null) {
            user.setPreferences(signUpRequest.getPreferences());
        }
        
        userRepository.save(user);
        
        return ResponseEntity.ok("User registered successfully!");
    }
    
    @PostMapping("/oauth/register")
    public ResponseEntity<?> registerOrLoginOAuthUser(@Valid @RequestBody OAuthUserRequest oauthRequest) {
        System.out.println("=== OAUTH LOGIN/REGISTRATION REQUEST ===");
        System.out.println("Provider: " + oauthRequest.getProvider());
        System.out.println("Email: " + oauthRequest.getEmail());

        // Check if user already exists by email
        User user = userRepository.findByEmail(oauthRequest.getEmail()).orElse(null);

        if (user != null) {
            // User exists - update OAuth info if needed
            System.out.println("Existing user found: " + user.getUsername());

            // Update provider info if it was previously a local account
            if ("local".equals(user.getProvider()) && !oauthRequest.getProvider().equals("local")) {
                user.setProvider(oauthRequest.getProvider());
                user.setProviderId(oauthRequest.getProviderId());
                user.setImageUrl(oauthRequest.getImageUrl());
                userRepository.save(user);
                System.out.println("Updated existing local account to OAuth account");
            }
        } else {
            // Create new OAuth user
            System.out.println("Creating new OAuth user");

            // Generate username from email if not provided
            String username = oauthRequest.getEmail().split("@")[0];

            // Ensure username is unique
            int counter = 1;
            String originalUsername = username;
            while (userRepository.existsByUsername(username)) {
                username = originalUsername + counter;
                counter++;
            }

            user = new User();
            user.setFirstName(oauthRequest.getFirstName());
            user.setLastName(oauthRequest.getLastName());
            user.setEmail(oauthRequest.getEmail());
            user.setUsername(username);
            user.setPassword(encoder.encode("OAUTH_USER_" + System.currentTimeMillis())); // Random password for OAuth users
            user.setProvider(oauthRequest.getProvider());
            user.setProviderId(oauthRequest.getProviderId());
            user.setImageUrl(oauthRequest.getImageUrl());
            user.setRole(oauthRequest.getRole());
            user.setEnabled(true);

            userRepository.save(user);
            System.out.println("New OAuth user created with username: " + username);
        }

        // Generate JWT token
        String jwt = jwtUtils.generateJwtToken(user.getUsername());

        JwtResponse response = new JwtResponse(
            jwt,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole(),
            user.getImageUrl(),
            user.getProvider()
        );

        System.out.println("Sending OAuth response for user: " + user.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Forgot Password - Generate and send OTP via email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        System.out.println("=== FORGOT PASSWORD REQUEST ===");
        System.out.println("Email: " + request.getEmail());

        try {
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);

            if (user == null) {
                // Don't reveal if user exists or not (security best practice)
                System.out.println("User not found, but returning success message anyway");
                return ResponseEntity.ok().body(java.util.Map.of(
                    "success", true,
                    "message", "If an account with this email exists, a password reset OTP has been sent."
                ));
            }

            // Generate 6-digit OTP
            String otp = generateOTP();
            System.out.println("========================================");
            System.out.println("Generated OTP for user: " + user.getUsername());
            System.out.println("OTP CODE: " + otp);
            System.out.println("========================================");

            // Hash the OTP before storing (same as SRI_EXPRESS approach)
            String hashedOtp = encoder.encode(otp);
            user.setResetPasswordOtp(hashedOtp);
            user.setResetPasswordOtpExpiry(LocalDateTime.now().plusHours(1)); // 1 hour expiry
            userRepository.save(user);

            // Send OTP via email
            try {
                emailService.sendPasswordResetOTP(user.getEmail(), otp, user.getFirstName());
                System.out.println("Password reset OTP sent successfully to: " + user.getEmail());
            } catch (Exception emailError) {
                System.err.println("Failed to send email: " + emailError.getMessage());
                // Clear OTP if email fails
                user.setResetPasswordOtp(null);
                user.setResetPasswordOtpExpiry(null);
                userRepository.save(user);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "success", false,
                    "message", "Failed to send email. Please try again later."
                ));
            }

            return ResponseEntity.ok().body(java.util.Map.of(
                "success", true,
                "message", "If an account with this email exists, a password reset OTP has been sent."
            ));

        } catch (Exception e) {
            System.err.println("Forgot password error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "An error occurred. Please try again later."
            ));
        }
    }

    /**
     * Reset Password - Verify OTP and reset password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        System.out.println("=== RESET PASSWORD REQUEST ===");
        System.out.println("Email: " + request.getEmail());

        try {
            // Find user by email with valid OTP
            User user = userRepository.findByEmail(request.getEmail()).orElse(null);

            if (user == null || user.getResetPasswordOtp() == null || user.getResetPasswordOtpExpiry() == null) {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Invalid OTP or expired reset request. Please try again."
                ));
            }

            // Check if OTP has expired
            if (user.getResetPasswordOtpExpiry().isBefore(LocalDateTime.now())) {
                System.out.println("OTP expired for user: " + user.getUsername());
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "OTP has expired. Please request a new one."
                ));
            }

            // Verify OTP matches (compare with hashed OTP)
            boolean isOtpValid = encoder.matches(request.getOtp(), user.getResetPasswordOtp());

            if (!isOtpValid) {
                System.out.println("Invalid OTP provided for user: " + user.getUsername());
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Invalid OTP. Please check and try again."
                ));
            }

            // Reset password
            user.setPassword(encoder.encode(request.getNewPassword()));
            user.setResetPasswordOtp(null); // Clear OTP
            user.setResetPasswordOtpExpiry(null); // Clear expiry
            userRepository.save(user);

            System.out.println("Password reset successful for user: " + user.getUsername());

            return ResponseEntity.ok().body(java.util.Map.of(
                "success", true,
                "message", "Password reset successful. You can now login with your new password."
            ));

        } catch (Exception e) {
            System.err.println("Reset password error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "An error occurred. Please try again later."
            ));
        }
    }

    /**
     * Generate a 6-digit OTP
     */
    private String generateOTP() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        System.out.println("Validation error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Validation error: " + ex.getMessage());
    }
}