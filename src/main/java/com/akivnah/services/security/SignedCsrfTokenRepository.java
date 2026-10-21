package com.akivnah.services.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cookie-free CSRF protection for this stateless (JWT) application.
 * The token is an HMAC of the visitor's JWT cookie, signed with the server secret, so it is recomputed on every
 * request instead of being stored in a second cookie (the "XSRF-TOKEN" cookie was not reaching the server, which
 * made every form post fail with MissingCsrfTokenException). A third-party site cannot read the page, so it cannot
 * learn the token. Visitors who are not logged in (login, register, contact) share one public token.
 */
public class SignedCsrfTokenRepository implements CsrfTokenRepository {
    private final byte[] key;

    public SignedCsrfTokenRepository(String secret) { this.key = secret.getBytes(StandardCharsets.UTF_8); }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", compute(request));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // nothing to store: the token is derived from the request
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) { return generateToken(request); }

    private String compute(HttpServletRequest request) {
        String jwt = JwtAuthFilter.resolveToken(request);
        String subject = jwt == null ? "anonymous" : "jwt:" + jwt;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute CSRF token", e);
        }
    }
}
