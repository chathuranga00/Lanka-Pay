package com.lankapay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smoke test: boots the full application context against Firestore emulator
 * and verifies health and security endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
        // If the Spring context fails to start, this test fails with a clear error.
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownEndpointReturns401NotFound() throws Exception {
        // Unauthenticated request to a protected endpoint should return 401
        mockMvc.perform(get("/api/v1/registry/lookup")
                        .queryParam("phone", "+94771234567")
                        .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized());
    }
}
