package com.akivnah.services.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ContactMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String service;
    @Column(length = 2000)
    private String message;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getService() { return service; }
    public void setService(String v) { this.service = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
