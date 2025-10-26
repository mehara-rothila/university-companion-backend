package com.smartuniversity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 50)
    private String firstName;
    
    @NotBlank
    @Size(max = 50)
    private String lastName;
    
    @NotBlank
    @Email
    @Size(max = 100)
    @Column(unique = true)
    private String email;
    
    @NotBlank
    @Size(max = 20)
    @Column(unique = true)
    private String username;
    
    @Size(max = 120)
    private String password;

    @Size(max = 20)
    private String studentId;

    @Size(max = 100)
    private String major;

    private Integer year;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.STUDENT;

    private boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<String> preferences = new HashSet<>();

    // OAuth fields
    @Size(max = 50)
    private String provider = "local"; // "local", "google", "github", etc.

    @Size(max = 100)
    private String providerId; // OAuth provider's unique user ID

    @Size(max = 500)
    private String imageUrl; // Profile picture URL

    // Password reset OTP fields
    @Size(max = 255)
    private String resetPasswordOtp; // Hashed OTP for password reset

    private LocalDateTime resetPasswordOtpExpiry; // OTP expiration time

    public enum UserRole {
        STUDENT, FACULTY, ADMIN
    }
    
    public User() {}
    
    public User(String firstName, String lastName, String email, String username, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.username = username;
        this.password = password;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Set.of(() -> "ROLE_" + role.name());
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Set<String> getPreferences() { return preferences; }
    public void setPreferences(Set<String> preferences) { this.preferences = preferences; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getResetPasswordOtp() { return resetPasswordOtp; }
    public void setResetPasswordOtp(String resetPasswordOtp) { this.resetPasswordOtp = resetPasswordOtp; }

    public LocalDateTime getResetPasswordOtpExpiry() { return resetPasswordOtpExpiry; }
    public void setResetPasswordOtpExpiry(LocalDateTime resetPasswordOtpExpiry) { this.resetPasswordOtpExpiry = resetPasswordOtpExpiry; }
}