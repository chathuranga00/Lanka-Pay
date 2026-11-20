package com.lankapay.support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.support.config.GeminiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiSupportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("isAvailable() returns false when API key is empty or not configured")
    void isAvailable_withoutKey() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("");
        GeminiSupportService service = new GeminiSupportService(properties, objectMapper);

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.askGemini("Hello", null)).isEmpty();
    }

    @Test
    @DisplayName("isAvailable() returns true when API key is set and enabled")
    void isAvailable_withKey() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key-12345");
        properties.setEnabled(true);
        GeminiSupportService service = new GeminiSupportService(properties, objectMapper);

        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Fallback returns empty Optional gracefully if API call fails")
    void askGemini_failsGracefully() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("invalid-key");
        properties.setEndpoint("http://localhost:59999/non-existent");
        GeminiSupportService service = new GeminiSupportService(properties, objectMapper);

        Optional<GeminiSupportService.GeminiAnswer> response = service.askGemini("How do I link a bank account?", null);
        assertThat(response).isEmpty();
    }
}
