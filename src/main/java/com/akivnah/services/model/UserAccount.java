package com.akivnah.services.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String username;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role = Role.USER;
    private Instant createdAt = Instant.now();
    @Column(length = 500)
    private String mailSignature;
    /** Tokens issued before this moment are rejected (set whenever the password changes). Nullable for old rows. */
    private Instant passwordChangedAt;
    /** True after an administrator reset: the user must choose a new password before doing anything else. */
    private Boolean mustChangePassword = false;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMailSignature() { return mailSignature; }
    public void setMailSignature(String v) { this.mailSignature = v; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(Instant v) { this.passwordChangedAt = v; }
    public boolean isMustChangePassword() { return Boolean.TRUE.equals(mustChangePassword); }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
}
