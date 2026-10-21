package com.akivnah.services.api;

import com.akivnah.services.model.ServiceItem;
import com.akivnah.services.repository.ServiceItemRepository;
import com.akivnah.services.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Stateless JSON API secured with "Authorization: Bearer <jwt>". */
@RestController
@RequestMapping("/api")
public class ApiController {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final ServiceItemRepository services;

    public ApiController(AuthenticationManager authManager, JwtService jwtService, ServiceItemRepository services) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.services = services;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            String token = jwtService.generateToken((UserDetails) auth.getPrincipal());
            return ResponseEntity.ok(Map.of("token", token, "tokenType", "Bearer",
                    "expiresInSeconds", jwtService.getExpirationSeconds()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
    }

    @GetMapping("/services")
    public List<ServiceItem> list() { return services.findByActiveTrueOrderByNameAsc(); }
}
