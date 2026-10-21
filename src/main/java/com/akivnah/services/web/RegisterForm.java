package com.akivnah.services.web;

import jakarta.validation.constraints.*;

public class RegisterForm {
    @NotBlank @Size(min = 3, max = 30)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Letters, numbers, dot, dash and underscore only")
    private String username;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters")
    private String password;
    @NotBlank
    private String confirmPassword;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
