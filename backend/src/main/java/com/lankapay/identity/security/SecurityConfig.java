package com.lankapay.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Roles: CUSTOMER, MERCHANT, ADMIN
 * - Public:   actuator health/info, all /auth/** endpoints, OpenAPI docs
 * - ADMIN:    /api/v1/admin/**
 * - Any auth: everything else (object-level checks done in services via @PreAuthorize)
 *
 * Stateless JWT – no session, no CSRF.
 * @EnableMethodSecurity enables @PreAuthorize on service methods.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth

                // ── Actuator ──────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()

                // ── Auth – all public ──────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-otp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()

                // logout and change-pin require a valid JWT (authenticated)
                .requestMatchers(HttpMethod.POST,  "/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/change-pin").authenticated()

                // ── OpenAPI (dev only) ─────────────────────────────
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // ── Admin ──────────────────────────────────────────
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // ── Everything else requires any authenticated role ─
                .anyRequest().authenticated()
            )

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt password encoder, cost 12.
     * Used for PIN hashing. OTPs use a separate BCrypt call in OtpService.
     * Never use this encoder for OTPs directly – keep them separate.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
