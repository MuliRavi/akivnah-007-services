package com.akivnah.services.config;

import com.akivnah.services.security.JwtAuthFilter;
import com.akivnah.services.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import com.akivnah.services.security.SignedCsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                    UserDetailsService userDetailsService,
                                    com.akivnah.services.repository.UserRepository userRepository,
                                    @Value("${akivnah.jwt.secret}") String jwtSecret) throws Exception {
        http
            // CSRF protects the cookie-based web pages; the stateless /api/** uses Bearer tokens
            .csrf(csrf -> csrf.csrfTokenRepository(new SignedCsrfTokenRepository(jwtSecret))
                              .ignoringRequestMatchers("/api/**", "/h2-console/**")
                              // public enquiry form: anonymous, no account to protect (a hidden honeypot field stops bots)
                              .ignoringRequestMatchers(AntPathRequestMatcher.antMatcher(org.springframework.http.HttpMethod.POST, "/contact")))
            .headers(h -> h.frameOptions(f -> f.sameOrigin())
                           // keeps the one-time token in /reset-password?token=... from leaking through the Referer header
                           .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/services", "/catalog", "/about", "/work", "/careers", "/insights", "/contact", "/forgot-password", "/reset-password",
                                 "/css/**", "/images/**", "/error/**", "/error", "/api/auth/**").permitAll()
                // H2 web console (only reachable when H2_CONSOLE=true) is for admins only
                .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/hr/**").hasAnyRole("HR", "ADMIN")
                .requestMatchers("/portal/**").hasAnyRole("EMPLOYEE", "HR", "ADMIN")
                .requestMatchers("/mail/**").hasAnyRole("EMPLOYEE", "HR", "ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex.accessDeniedHandler((req, res, e) -> {
                    // say in the console WHY a 403 happened (missing/invalid CSRF token, or a role problem)
                    org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).warn("403 {} {} -> {}: {}",
                            req.getMethod(), req.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
                    res.sendError(403);
                }).authenticationEntryPoint((req, res, e) -> {
                if (req.getRequestURI().startsWith("/api/")) res.sendError(401, "Unauthorized");
                else res.sendRedirect(req.getContextPath() + "/login");
            }))
            .logout(l -> l.logoutUrl("/logout")
                          .addLogoutHandler((req, res, auth) -> {
                              String token = JwtAuthFilter.resolveToken(req);
                              if (token != null) jwtService.revoke(token);
                          })
                          .addLogoutHandler(new HeaderWriterLogoutHandler(
                                  new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.CACHE)))
                          .deleteCookies(JwtAuthFilter.COOKIE_NAME)
                          .logoutSuccessUrl("/login?logout"))
            .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService, userRepository),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

}
