package com.smartuniversity.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String usernameOrEmail;

    @NotBlank
    private String password;

    public LoginRequest() {}

    public LoginRequest(String usernameOrEmail, String password) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }

    public String getUsernameOrEmail() { return usernameOrEmail; }
    public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }

    // Keep legacy getter/setter for backward compatibility
    public String getUsername() { return usernameOrEmail; }
    public void setUsername(String username) { this.usernameOrEmail = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}