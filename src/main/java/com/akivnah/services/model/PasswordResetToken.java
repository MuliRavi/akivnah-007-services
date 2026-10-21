package com.akivnah.services.model;

import jakarta.persistence.*;
import java.time.Instant;

/** A one-time password reset link. Only the SHA-256 hash of the token is stored, never the token itself. */
@Entity
@Table(indexes = @Index(name = "idx_reset_hash", columnList = "tokenHash", unique = true))
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private UserAccount user;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount v) { this.user = v; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String v) { this.tokenHash = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant v) { this.usedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
}
