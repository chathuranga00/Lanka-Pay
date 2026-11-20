package com.lankapay.feature.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.feature.config.FeatureProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagService unit tests")
class FeatureFlagServiceTest {

    @Mock
    private AuditService auditService;

    private FeatureProperties properties;
    private FeatureFlagService service;

    @BeforeEach
    void setUp() {
        properties = new FeatureProperties();
        // verify safe defaults
        service = new FeatureFlagService(properties, auditService);
    }

    @Test
    @DisplayName("Safe defaults: all newly planned modules are false by default")
    void safeDefaultsAreDisabled() {
        Map<String, Boolean> features = service.getAllFeatures();
        assertThat(features).isNotEmpty();
        assertThat(service.isEnabled("stories")).isFalse();
        assertThat(service.isEnabled("bills")).isFalse();
        assertThat(service.isEnabled("scheduled")).isFalse();
        assertThat(service.isEnabled("split")).isFalse();
        assertThat(service.isEnabled("rewards")).isFalse();
        assertThat(service.isEnabled("city")).isFalse();
        assertThat(service.isEnabled("investments")).isFalse();
        assertThat(service.isEnabled("unknown_feature")).isFalse();
    }

    @Test
    @DisplayName("Admin can toggle individual feature flag and action is audited")
    void adminCanToggleFeatureFlag() {
        assertThat(service.isEnabled("bills")).isFalse();

        service.setFeature("bills", true, 99L, "192.168.1.1");

        assertThat(service.isEnabled("bills")).isTrue();
        verify(auditService).log(
                eq(99L),
                eq("FEATURE_FLAG_TOGGLED"),
                eq("FEATURE_FLAG"),
                any(),
                any(),
                eq("192.168.1.1")
        );
    }

    @Test
    @DisplayName("Admin can bulk update feature flags")
    void adminCanBulkUpdateFeatureFlags() {
        service.setFeatures(Map.of(
                "stories", true,
                "city", true
        ), 99L, "10.0.0.1");

        assertThat(service.isEnabled("stories")).isTrue();
        assertThat(service.isEnabled("city")).isTrue();
        assertThat(service.isEnabled("bills")).isFalse();
    }
}
