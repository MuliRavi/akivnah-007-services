package com.akivnah.services.web;

import com.akivnah.services.model.PasswordResetToken;
import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.PasswordResetTokenRepository;
import com.akivnah.services.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Forgot / reset / change password. Tokens are random, single-use, hashed in the database and expire after 30 minutes. */
@Service
public class PasswordService {
    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final String TEMP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String publicUrl;
    private final String from;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                           ObjectProvider<JavaMailSender> mailSender,
                           @Value("${akivnah.public-url:http://localhost:8080}") String publicUrl,
                           @Value("${akivnah.mail.from:}") String configuredFrom,
                           @Value("${spring.mail.username:}") String smtpUser) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.from = !configuredFrom.isBlank() ? configuredFrom : smtpUser;
    }

    /** Returns an error message, or null if the password is acceptable (8-72 characters; 72 is BCrypt's limit). */
    public static String check(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 72) return "Use 8 to 72 characters";
        return null;
    }

    /** Always silent about whether the account exists, so the form cannot be used to find usernames or emails. */
    @Transactional
    public void requestReset(String identifier) {
        if (identifier == null || identifier.isBlank()) return;
        String id = identifier.trim();
        Optional<UserAccount> found = users.findByUsername(id);
        if (found.isEmpty()) found = users.findByEmailIgnoreCase(id);
        if (found.isEmpty()) return;
        UserAccount u = found.get();
        if (tokens.countByUserAndCreatedAtAfter(u, Instant.now().minus(Duration.ofHours(1))) >= MAX_REQUESTS_PER_HOUR) {
            log.warn("Password reset rate limit reached for user {}", u.getUsername());
            return;
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(u);
        t.setTokenHash(hash(token));
        t.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokens.save(t);
        send(u, publicUrl + "/reset-password?token=" + token);
    }

    private void send(UserAccount u, String link) {
        JavaMailSender s = mailSender.getIfAvailable();
        if (s == null || from.isBlank()) {
            // Development fallback when SMTP is not configured. Never do this in production: set SPRING_MAIL_* instead.
            log.warn("SMTP is not configured. Password reset link for {} (valid 30 minutes): {}", u.getUsername(), link);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(u.getEmail());
            m.setSubject("Reset your Akivnah password");
            m.setText("Hello " + u.getUsername() + ",\n\nUse this link to choose a new password. It works once and expires in 30 minutes:\n\n"
                    + link + "\n\nIf you did not ask for this, ignore this email; your password has not changed.\n\nAkivnah");
            s.send(m);
        } catch (RuntimeException e) {
            log.error("Could not send the reset email to {}: {}", u.getUsername(), e.getMessage());
        }
    }

    /** The user for a valid, unused, unexpired token. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> userForToken(String token) {
        return find(token).map(PasswordResetToken::getUser);
    }

    private Optional<PasswordResetToken> find(String token) {
        if (token == null || token.isBlank() || token.length() > 100) return Optional.empty();
        return tokens.findByTokenHash(hash(token))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()));
    }

    /** @return true if the token was valid and the password was changed */
    @Transactional
    public boolean resetWithToken(String token, String newPassword) {
        Optional<PasswordResetToken> t = find(token);
        if (t.isEmpty()) return false;
        UserAccount u = t.get().getUser();
        setPassword(u, newPassword);
        tokens.deleteAllForUser(u);   // this token and any other outstanding ones are now useless
        return true;
    }

    /** The user chose this password themselves: signs out every older session. */
    @Transactional
    public void setPassword(UserAccount u, String newPassword) { setPassword(u, newPassword, false); }

    /** @param mustChange true for a temporary password set by HR/admin: the user must pick their own at next sign-in */
    @Transactional
    public void setPassword(UserAccount u, String newPassword, boolean mustChange) {
        u.setPassword(encoder.encode(newPassword));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(mustChange);
        users.save(u);
    }

    public boolean matches(UserAccount u, String rawPassword) {
        return rawPassword != null && encoder.matches(rawPassword, u.getPassword());
    }

    /** Random temporary password for an HR/admin reset (no look-alike characters). */
    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(TEMP_CHARS.charAt(random.nextInt(TEMP_CHARS.length())));
        return sb.toString();
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void purgeExpired() { tokens.deleteExpired(Instant.now()); }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
