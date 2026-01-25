package com.smartuniversity.controller;

import com.smartuniversity.dto.JwtResponse;
import com.smartuniversity.dto.LoginRequest;
import com.smartuniversity.dto.SignupRequest;
import com.smartuniversity.dto.OAuthUserRequest;
import com.smartuniversity.dto.ForgotPasswordRequest;
import com.smartuniversity.dto.ResetPasswordRequest;
import com.smartuniversity.dto.VerifyEmailRequest;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Allowed university email domains
    private static final List<String> ALLOWED_EMAIL_DOMAINS = Arrays.asList(
        "@uom.lk",
        "@mrt.ac.lk",
        "@student.mrt.ac.lk"
    );

    /**
     * Validate if email belongs to an allowed university domain
     */
    private boolean isUniversityEmail(String email) {
        if (email == null) return false;
        String lowerEmail = email.toLowerCase();
        return ALLOWED_EMAIL_DOMAINS.stream().anyMatch(lowerEmail::endsWith);
    }
    
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

        System.out.println("User found: " + user.getUsername() + " (email: " + user.getEmail() + ")");

        // VERIFY PASSWORD - This was missing before!
        if (!encoder.matches(loginRequest.getPassword(), user.getPassword())) {
            System.out.println("Invalid password for user: " + user.getUsername());
            return ResponseEntity.badRequest().body("Error: Invalid username/email or password!");
        }

        System.out.println("Password verified successfully for user: " + user.getUsername());

        // Check if email is verified (only for local accounts, not OAuth)
        if ("local".equals(user.getProvider()) && !user.isEmailVerified()) {
            System.out.println("Email not verified for user: " + user.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of(
                "error", "EMAIL_NOT_VERIFIED",
                "message", "Please verify your email before logging in.",
                "email", user.getEmail()
            ));
        }

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
        // Validate university email domain
        if (!isUniversityEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Only university email addresses (@uom.lk, @mrt.ac.lk) are allowed to register."
            ));
        }

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Username is already taken!"
            ));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Email is already in use!"
            ));
        }

        User user = new User(signUpRequest.getFirstName(),
                            signUpRequest.getLastName(),
                            signUpRequest.getEmail(),
                            signUpRequest.getUsername(),
                            encoder.encode(signUpRequest.getPassword()));

        user.setStudentId(signUpRequest.getStudentId());
        user.setMajor(signUpRequest.getMajor());
        user.setYear(signUpRequest.getYear());
        // Security: Always set role to STUDENT for self-registration
        // Admin/Faculty roles should only be assigned by existing admins
        user.setRole(UserRole.STUDENT);
        // Email verification required
        user.setEmailVerified(false);
        if (signUpRequest.getPreferences() != null) {
            user.setPreferences(signUpRequest.getPreferences());
        }

        // Generate and send verification OTP
        String otp = generateOTP();
        System.out.println("========================================");
        System.out.println("Generated Email Verification OTP for: " + user.getEmail());
        System.out.println("OTP CODE: " + otp);
        System.out.println("========================================");

        user.setEmailVerificationOtp(encoder.encode(otp));
        user.setEmailVerificationOtpExpiry(LocalDateTime.now().plusHours(1));

        userRepository.save(user);

        // Send verification email
        try {
            emailService.sendEmailVerificationOTP(user.getEmail(), otp, user.getFirstName());
            System.out.println("Verification email sent to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
            // User is saved but email failed - they can request resend
        }

        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "message", "Registration successful! Please check your email for the verification code.",
            "email", user.getEmail()
        ));
    }

    /**
     * Verify email with OTP
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        System.out.println("=== EMAIL VERIFICATION REQUEST ===");
        System.out.println("Email: " + request.getEmail());

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "User not found with this email."
            ));
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Email is already verified. You can login now."
            ));
        }

        if (user.getEmailVerificationOtp() == null || user.getEmailVerificationOtpExpiry() == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "No verification code found. Please request a new one."
            ));
        }

        // Check if OTP has expired
        if (user.getEmailVerificationOtpExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Verification code has expired. Please request a new one."
            ));
        }

        // Verify OTP
        if (!encoder.matches(request.getOtp(), user.getEmailVerificationOtp())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Invalid verification code. Please check and try again."
            ));
        }

        // Mark email as verified
        user.setEmailVerified(true);
        user.setEmailVerificationOtp(null);
        user.setEmailVerificationOtpExpiry(null);
        userRepository.save(user);

        System.out.println("Email verified successfully for: " + user.getEmail());

        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "message", "Email verified successfully! You can now login."
        ));
    }

    /**
     * Resend email verification OTP
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        System.out.println("=== RESEND VERIFICATION REQUEST ===");
        System.out.println("Email: " + email);

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", "Email is required."
            ));
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Don't reveal if user exists
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "If an account with this email exists, a verification code has been sent."
            ));
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Email is already verified. You can login now."
            ));
        }

        // Generate new OTP
        String otp = generateOTP();
        System.out.println("========================================");
        System.out.println("Resending Email Verification OTP for: " + user.getEmail());
        System.out.println("OTP CODE: " + otp);
        System.out.println("========================================");

        user.setEmailVerificationOtp(encoder.encode(otp));
        user.setEmailVerificationOtpExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendEmailVerificationOTP(user.getEmail(), otp, user.getFirstName());
            System.out.println("Verification email resent to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to resend verification email: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                "success", false,
                "message", "Failed to send email. Please try again later."
            ));
        }

        return ResponseEntity.ok(java.util.Map.of(
            "success", true,
            "message", "Verification code sent! Please check your email."
        ));
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
            // Validate university email domain for NEW OAuth users
            if (!isUniversityEmail(oauthRequest.getEmail())) {
                System.out.println("OAuth registration rejected - non-university email: " + oauthRequest.getEmail());
                return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "Only university email addresses (@uom.lk, @mrt.ac.lk) are allowed to register.",
                    "message", "Please sign in with your university Google account."
                ));
            }

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
            // Security: Always set role to STUDENT for self-registration
            user.setRole(UserRole.STUDENT);
            user.setEnabled(true);
            // OAuth providers (Google) already verify email
            user.setEmailVerified(true);

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