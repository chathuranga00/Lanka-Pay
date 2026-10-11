package com.lankapay.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.AccountLockedException;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.TokenExpiredException;
import com.lankapay.identity.security.JwtService;
import com.lankapay.identity.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for AuthController.
 *
 * Uses @SpringBootTest so the real Security filter chain is active.
 * AuthService is mocked — no business logic runs here.
 * Real JwtService is wired to validate real test tokens.
 *
 * Runs against the Firestore Emulator (FIRESTORE_EMULATOR_HOST).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean AuthService authService;
    // Keep the real JwtService wired – it uses the test secret from application-test.yml
    // We only need to mock AuthService (the business layer).

    static final String BASE = "/api/v1/auth";

    // ── Helper: build a real JWT for authenticated endpoints ──────

    @Autowired JwtService jwtService;

    String customerJwt() {
        return "Bearer " + jwtService.generateToken(1L, "CUSTOMER");
    }

    String adminJwt() {
        return "Bearer " + jwtService.generateToken(99L, "ADMIN");
    }

    // ── /register ──────────────────────────────────────────────

    @Nested @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("202 on valid request")
        void success() throws Exception {
            when(authService.register(any(), any()))
                    .thenReturn(new RegisterResponse(1L, Instant.now().plusSeconds(300)));

            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","fullName":"Kamal Perera",
                                     "nic":"199012345678","pin":"123456","role":"CUSTOMER"}
                                    """))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.otpExpiresAt").exists());
        }

        @Test
        @DisplayName("400 when phone is blank")
        void missingPhone() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fullName":"Kamal","nic":"199012345678","pin":"123456"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("400 when phone format is wrong")
        void badPhoneFormat() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"07711234567","fullName":"Kamal",
                                     "nic":"199012345678","pin":"123456"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("400 when PIN is not 6 digits")
        void badPin() throws Exception {
            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","fullName":"Kamal",
                                     "nic":"199012345678","pin":"12"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("409 PHONE_ALREADY_REGISTERED")
        void duplicatePhone() throws Exception {
            when(authService.register(any(), any()))
                    .thenThrow(new ConflictException("PHONE_ALREADY_REGISTERED",
                            "Phone already registered"));

            mockMvc.perform(post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","fullName":"Kamal",
                                     "nic":"199012345678","pin":"123456"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
        }
    }

    // ── /verify-otp ────────────────────────────────────────────

    @Nested @DisplayName("POST /verify-otp")
    class VerifyOtp {

        @Test
        @DisplayName("200 with token pair on success")
        void success() throws Exception {
            when(authService.verifyOtp(eq(1L), eq("123456"), any()))
                    .thenReturn(TokenResponse.full("jwt-token", "refresh-token", 1800));

            mockMvc.perform(post(BASE + "/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":1,"otp":"123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.expiresIn").value(1800));
        }

        @Test
        @DisplayName("400 OTP_INVALID on wrong code")
        void wrongOtp() throws Exception {
            when(authService.verifyOtp(any(), any(), any()))
                    .thenThrow(OtpException.invalid());

            mockMvc.perform(post(BASE + "/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":1,"otp":"000000"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }

        @Test
        @DisplayName("410 OTP_EXPIRED when TTL elapsed")
        void expiredOtp() throws Exception {
            when(authService.verifyOtp(any(), any(), any()))
                    .thenThrow(OtpException.expired());

            mockMvc.perform(post(BASE + "/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":1,"otp":"123456"}
                                    """))
                    .andExpect(status().is(410))
                    .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
        }

        @Test
        @DisplayName("429 OTP_MAX_ATTEMPTS")
        void maxAttempts() throws Exception {
            when(authService.verifyOtp(any(), any(), any()))
                    .thenThrow(OtpException.maxAttemptsReached());

            mockMvc.perform(post(BASE + "/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":1,"otp":"000000"}
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("OTP_MAX_ATTEMPTS"));
        }
    }

    // ── /login ─────────────────────────────────────────────────

    @Nested @DisplayName("POST /login")
    class LoginEndpoint {

        @Test
        @DisplayName("200 with token pair on success")
        void success() throws Exception {
            when(authService.login(eq("+94771234567"), eq("123456"), any()))
                    .thenReturn(TokenResponse.full("jwt", "refresh", 1800));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","pin":"123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh"));
        }

        @Test
        @DisplayName("401 INVALID_CREDENTIALS on wrong password")
        void wrongPassword() throws Exception {
            when(authService.login(any(), any(), any()))
                    .thenThrow(new InvalidCredentialsException());

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","pin":"999999"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("429 ACCOUNT_LOCKED")
        void accountLocked() throws Exception {
            when(authService.login(any(), any(), any()))
                    .thenThrow(new AccountLockedException(Instant.now().plusSeconds(900)));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","pin":"999999"}
                                    """))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        }

        @Test
        @DisplayName("403 ACCOUNT_FROZEN")
        void frozenAccount() throws Exception {
            when(authService.login(any(), any(), any()))
                    .thenThrow(new AuthorizationException("ACCOUNT_FROZEN", "Frozen"));

            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"+94771234567","pin":"123456"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
        }

        @Test
        @DisplayName("400 on missing body fields")
        void missingFields() throws Exception {
            mockMvc.perform(post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    // ── /refresh ───────────────────────────────────────────────

    @Nested @DisplayName("POST /refresh")
    class RefreshEndpoint {

        @Test
        @DisplayName("200 returns new access token only (no refresh token in body)")
        void success() throws Exception {
            when(authService.refresh(eq("old-token"), any()))
                    .thenReturn(TokenResponse.full("new-jwt", "ignored", 1800));

            mockMvc.perform(post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"old-token"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-jwt"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist());
        }

        @Test
        @DisplayName("401 TOKEN_EXPIRED on expired/revoked token")
        void expiredToken() throws Exception {
            when(authService.refresh(any(), any()))
                    .thenThrow(new TokenExpiredException());

            mockMvc.perform(post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"expired"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }
    }

    // ── /logout ────────────────────────────────────────────────

    @Nested @DisplayName("POST /logout")
    class LogoutEndpoint {

        @Test
        @DisplayName("204 on valid authenticated logout")
        void success() throws Exception {
            doNothing().when(authService).logout(any(), any(), any());

            mockMvc.perform(post(BASE + "/logout")
                            .header("Authorization", customerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"my-token"}
                                    """))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("401 when no JWT provided")
        void unauthenticated() throws Exception {
            mockMvc.perform(post(BASE + "/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── /change-pin ────────────────────────────────────────────

    @Nested @DisplayName("PATCH /change-pin")
    class ChangePinEndpoint {

        @Test
        @DisplayName("200 on valid authenticated request")
        void success() throws Exception {
            doNothing().when(authService).changePin(any(), any(), any(), any());

            mockMvc.perform(patch(BASE + "/change-pin")
                            .header("Authorization", customerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPin":"123456","newPin":"654321"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("PIN updated successfully"));
        }

        @Test
        @DisplayName("401 INVALID_CREDENTIALS on wrong current PIN")
        void wrongCurrentPin() throws Exception {
            doThrow(new InvalidCredentialsException())
                    .when(authService).changePin(any(), any(), any(), any());

            mockMvc.perform(patch(BASE + "/change-pin")
                            .header("Authorization", customerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPin":"000000","newPin":"654321"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("401 when no JWT provided")
        void unauthenticated() throws Exception {
            mockMvc.perform(patch(BASE + "/change-pin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPin":"123456","newPin":"654321"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("400 validation error when PIN is not 6 digits")
        void badPinFormat() throws Exception {
            mockMvc.perform(patch(BASE + "/change-pin")
                            .header("Authorization", customerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPin":"12","newPin":"654321"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    // ── Role enforcement ───────────────────────────────────────

    @Nested @DisplayName("Role enforcement")
    class RoleEnforcement {

        @Test
        @DisplayName("CUSTOMER JWT cannot access /admin endpoints → 403")
        void customerBlockedFromAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", customerJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN JWT can access /admin endpoints → 200")
        void adminAllowed() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .header("Authorization", adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("No JWT → 401 on protected endpoint")
        void noJwtOnProtectedEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/registry/lookup")
                            .queryParam("phone", "+94771234567"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
