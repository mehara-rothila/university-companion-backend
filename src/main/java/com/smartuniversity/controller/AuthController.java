package com.smartuniversity.controller;

import com.smartuniversity.dto.JwtResponse;
import com.smartuniversity.dto.LoginRequest;
import com.smartuniversity.dto.SignupRequest;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import com.smartuniversity.service.UserPrincipal;
import com.smartuniversity.model.User.UserRole;
import jakarta.validation.Valid;
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
        // For now, skip password validation and return a simple JWT
        String jwt = "fake-jwt-token";

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
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        System.out.println("Validation error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Validation error: " + ex.getMessage());
    }
}