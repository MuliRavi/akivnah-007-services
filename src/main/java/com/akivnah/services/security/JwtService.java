package com.akivnah.services.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;
    private final TokenBlacklist blacklist;

    public JwtService(@Value("${akivnah.jwt.secret}") String secret,
                      @Value("${akivnah.jwt.expiration-minutes}") long minutes,
                      TokenBlacklist blacklist) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // needs >= 32 bytes
        this.expirationSeconds = minutes * 60;
        this.blacklist = blacklist;
    }

    public String generateToken(UserDetails user) {
        String roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .reduce((a, b) -> a + "," + b).orElse("");
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claim("roles", roles)
                .issuer("akivnah-services")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** Username if the token is valid, not expired and not revoked, otherwise empty. */
    public Optional<String> extractUsername(String token) {
        try {
            Claims claims = parse(token);
            if (blacklist.isRevoked(claims.getId())) return Optional.empty();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Who the token belongs to and when it was issued, if it is valid, not expired and not revoked. */
    public record TokenInfo(String username, java.time.Instant issuedAt) {}

    public Optional<TokenInfo> inspect(String token) {
        try {
            Claims claims = parse(token);
            if (blacklist.isRevoked(claims.getId()) || claims.getSubject() == null) return Optional.empty();
            Date iat = claims.getIssuedAt();
            return Optional.of(new TokenInfo(claims.getSubject(), iat == null ? null : iat.toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Called at logout so the token stops working immediately. */
    public void revoke(String token) {
        try {
            Claims claims = parse(token);
            blacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // already invalid
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}
