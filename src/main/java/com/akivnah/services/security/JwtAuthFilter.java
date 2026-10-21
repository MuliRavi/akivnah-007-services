package com.akivnah.services.security;

import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.temporal.ChronoUnit;

/**
 * Reads the JWT from "Authorization: Bearer ..." (API) or the AKV_TOKEN cookie (web pages).
 * A token is refused when it was revoked, when the password changed after it was issued, or (for pages other than
 * the change-password page) when an administrator reset the password and the user has not chosen a new one yet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "AKV_TOKEN";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService, UserRepository users) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(req);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var info = jwtService.inspect(token);
            UserAccount acct = info.flatMap(i -> users.findByUsername(i.username())).orElse(null);
            if (acct != null && !issuedBeforePasswordChange(info.get().issuedAt(), acct)) {
                try {
                    UserDetails user = userDetailsService.loadUserByUsername(acct.getUsername());
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (UsernameNotFoundException ignored) {
                    // token for a deleted user: stay anonymous
                }
                if (acct.isMustChangePassword() && !allowedWhileChangingPassword(req)) {
                    String uri = req.getRequestURI();
                    if (uri.startsWith(req.getContextPath() + "/api/")) {
                        res.sendError(403, "Password change required");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/account/password?required");
                    }
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean issuedBeforePasswordChange(java.time.Instant issuedAt, UserAccount acct) {
        if (acct.getPasswordChangedAt() == null) return false;
        if (issuedAt == null) return true;
        return issuedAt.isBefore(acct.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS));
    }

    private static boolean allowedWhileChangingPassword(HttpServletRequest req) {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        return path.equals("/account/password") || path.equals("/logout") || path.startsWith("/css/")
                || path.startsWith("/images/") || path.startsWith("/error");
    }

    public static String resolveToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (COOKIE_NAME.equals(c.getName())) return c.getValue();
            }
        }
        return null;
    }
}
