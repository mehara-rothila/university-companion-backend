package com.smartuniversity.dto;

import com.smartuniversity.model.User;

public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private User.UserRole role;
    private String imageUrl;
    private String provider;

    public JwtResponse(String accessToken, Long id, String username, String email,
                      String firstName, String lastName, User.UserRole role) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    public JwtResponse(String accessToken, Long id, String username, String email,
                      String firstName, String lastName, User.UserRole role,
                      String imageUrl, String provider) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.imageUrl = imageUrl;
        this.provider = provider;
    }
    
    public String getAccessToken() { return token; }
    public void setAccessToken(String accessToken) { this.token = accessToken; }
    
    public String getTokenType() { return type; }
    public void setTokenType(String tokenType) { this.type = tokenType; }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public User.UserRole getRole() { return role; }
    public void setRole(User.UserRole role) { this.role = role; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}