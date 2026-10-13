package com.lankapay.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * Roles: CUSTOMER, MERCHANT, ADMIN
 * - Public:   actuator health/info, all /auth/** endpoints, OpenAPI docs
 * - ADMIN:    /api/v1/admin/**
 * - Any auth: everything else (object-level checks done in services via @PreAuthorize)
 *
 * Stateless JWT – no session, no CSRF.
 * Security hardened: CORS, HSTS, CSP, FrameOptions, ReferrerPolicy, PermissionsPolicy.
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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))

            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
            )

            .authorizeHttpRequests(auth -> auth

                // ── Actuator ──────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()

                // ── Auth – all public ──────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-otp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/cleanup").permitAll()

                // logout and change-pin require a valid JWT (authenticated)
                .requestMatchers(HttpMethod.POST,  "/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/change-pin").authenticated()

                // phone verification requires a valid JWT
                .requestMatchers("/api/v1/auth/verify-phone/**").authenticated()

                // payment profile requires a valid JWT
                .requestMatchers("/api/v1/payment-profile/**").authenticated()

                // bank accounts require a valid JWT
                .requestMatchers("/api/v1/bank-accounts/**").authenticated()
                .requestMatchers("/api/v1/bank-institutions").authenticated()

                // cards require a valid JWT
                .requestMatchers("/api/v1/cards/**").authenticated()

                // simulated savings goals require a valid JWT
                .requestMatchers("/api/v1/goals/**").authenticated()

                // simulated card vault — requires JWT (abuse is attributable to a user)
                .requestMatchers("/sim-vault/**").authenticated()

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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "X-Step-Up-Pin", "X-Correlation-Id", "If-Match", "Accept"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
