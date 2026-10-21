package com.akivnah.services.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tokens revoked at logout, kept until they would have expired anyway. In-memory: use Redis/DB if you run several instances. */
@Component
public class TokenBlacklist {
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null) return;
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
        revoked.put(tokenId, expiresAt);
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && revoked.containsKey(tokenId);
    }
}
