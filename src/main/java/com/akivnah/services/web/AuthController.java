package com.akivnah.services.web;

import com.akivnah.services.model.Role;
import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.UserRepository;
import com.akivnah.services.security.JwtAuthFilter;
import com.akivnah.services.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean cookieSecure;

    public AuthController(AuthenticationManager authManager, JwtService jwtService, UserRepository users,
                          PasswordEncoder encoder, @Value("${akivnah.jwt.cookie-secure}") boolean cookieSecure) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.users = users;
        this.encoder = encoder;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpServletResponse res) {
        try {
            var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            String token = jwtService.generateToken((UserDetails) auth.getPrincipal());
            ResponseCookie cookie = ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, token)
                    .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/")
                    .maxAge(jwtService.getExpirationSeconds()).build();
            res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return "redirect:/dashboard";
        } catch (AuthenticationException e) {
            return "redirect:/login?error";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (users.existsByUsername(form.getUsername())) {
            result.rejectValue("username", "taken", "Username already taken");
        }
        if (users.existsByEmail(form.getEmail())) {
            result.rejectValue("email", "taken", "Email already registered");
        }
        if (result.hasErrors()) return "register";

        UserAccount u = new UserAccount();
        u.setUsername(form.getUsername());
        u.setEmail(form.getEmail());
        u.setPassword(encoder.encode(form.getPassword()));
        u.setRole(Role.USER);
        users.save(u);
        return "redirect:/login?registered";
    }
}
