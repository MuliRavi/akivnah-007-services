package com.akivnah.services.web;

import jakarta.validation.constraints.*;

public class ContactForm {
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Email @Size(max = 150)
    private String email;
    @Size(max = 60)
    private String service = "Custom software";
    @NotBlank @Size(max = 2000)
    private String message;
    private String website; // honeypot: real visitors leave it empty

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getService() { return service; }
    public void setService(String v) { this.service = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { this.website = v; }
}
