package com.lankapay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smoke test: boots the full application context against a real MySQL 8
 * instance managed by Testcontainers, runs Flyway migrations, and verifies
 * the health endpoint returns UP.
 *
 * Run with: mvn test -Dtest=SmokeTest
 * Requires Docker to be running on the host.
 * Skipped automatically when Docker is unavailable (CI without Docker, etc.).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ExtendWith(SmokeTest.DockerAvailableCondition.class)
class SmokeTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lankapay_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4",
                         "--collation-server=utf8mb4_unicode_ci",
                         "--default-time-zone=+00:00");

    /**
     * Override datasource properties at test runtime so Spring Boot
     * connects to the Testcontainers MySQL instance.
     */
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                     () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
        // If the Spring context fails to start (bad config, Flyway error,
        // entity mapping mismatch), this test fails with a clear error.
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

    /**
     * JUnit 5 ExecutionCondition that skips the entire test class
     * when Docker is not available on the host.
     * This prevents a hard ERROR in environments without Docker Desktop.
     */
    static class DockerAvailableCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                org.testcontainers.DockerClientFactory.instance().client();
                return ConditionEvaluationResult.enabled("Docker is available");
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled(
                        "Skipping SmokeTest – Docker is not available: " + e.getMessage());
            }
        }
    }
}
