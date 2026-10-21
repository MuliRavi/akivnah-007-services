package com.akivnah.services.web;

import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import com.akivnah.services.security.JwtAuthFilter;
import com.akivnah.services.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Forgot password, reset by e-mailed link, and change password while signed in. */
@Controller
public class PasswordController {
    private final PasswordService passwords;
    private final UserRepository users;
    private final JwtService jwt;

    public PasswordController(PasswordService passwords, UserRepository users, JwtService jwt) {
        this.passwords = passwords;
        this.users = users;
        this.jwt = jwt;
    }

    // ---- forgot ----
    @GetMapping("/forgot-password")
    public String forgotForm() { return "forgot-password"; }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam(defaultValue = "") String identifier) {
        passwords.requestReset(identifier);
        return "redirect:/forgot-password?sent";   // same answer whether or not the account exists
    }

    // ---- reset with the emailed link ----
    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(defaultValue = "") String token, Model model) {
        boolean valid = passwords.userForToken(token).isPresent();
        model.addAttribute("valid", valid);
        model.addAttribute("token", valid ? token : "");
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam(defaultValue = "") String token,
                        @RequestParam(defaultValue = "") String password,
                        @RequestParam(defaultValue = "") String confirmPassword, Model model) {
        String error = PasswordService.check(password);
        if (error == null && !password.equals(confirmPassword)) error = "Passwords do not match";
        if (error != null) {
            model.addAttribute("valid", passwords.userForToken(token).isPresent());
            model.addAttribute("token", token);
            model.addAttribute("error", error);
            return "reset-password";
        }
        if (!passwords.resetWithToken(token, password)) {
            model.addAttribute("valid", false);
            model.addAttribute("token", "");
            return "reset-password";
        }
        return "redirect:/login?reset";
    }

    // ---- change while signed in ----
    @GetMapping("/account/password")
    public String changeForm() { return "account-password"; }

    @PostMapping("/account/password")
    public String change(@RequestParam(defaultValue = "") String currentPassword,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "") String confirmPassword,
                         Authentication auth, HttpServletRequest req, HttpServletResponse res, Model model) {
        UserAccount u = users.findByUsername(auth.getName()).orElseThrow();
        String error = null;
        if (!passwords.matches(u, currentPassword)) error = "Current password is wrong";
        else if ((error = PasswordService.check(password)) == null) {
            if (!password.equals(confirmPassword)) error = "New passwords do not match";
            else if (password.equals(currentPassword)) error = "Choose a password different from the current one";
        }
        if (error != null) {
            model.addAttribute("error", error);
            return "account-password";
        }
        passwords.setPassword(u, password);
        // every session, including this one, is now invalid: sign in again with the new password
        String token = JwtAuthFilter.resolveToken(req);
        if (token != null) jwt.revoke(token);
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, "")
                .httpOnly(true).sameSite("Lax").path("/").maxAge(0).build().toString());
        return "redirect:/login?pwchanged";
    }
}
