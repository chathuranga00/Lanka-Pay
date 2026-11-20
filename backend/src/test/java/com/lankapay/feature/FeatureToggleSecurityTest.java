package com.lankapay.feature;

import com.lankapay.feature.annotation.FeatureToggle;
import com.lankapay.feature.config.FeatureProperties;
import com.lankapay.feature.exception.FeatureDisabledException;
import com.lankapay.feature.interceptor.FeatureToggleInterceptor;
import com.lankapay.feature.service.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureToggleSecurityTest {

    private FeatureProperties properties;
    private FeatureFlagService featureFlagService;
    private FeatureToggleInterceptor interceptor;

    @RestController
    @FeatureToggle("bills")
    static class MockBillsController {
        @GetMapping("/api/v1/bills")
        public String getBills() {
            return "bills-data";
        }

        @GetMapping("/api/v1/bills/sub")
        @FeatureToggle("bills")
        public String getSubBills() {
            return "sub-bills-data";
        }
    }

    @RestController
    static class MockPublicController {
        @GetMapping("/api/v1/public")
        public String getPublic() {
            return "public-data";
        }
    }

    @BeforeEach
    void setUp() {
        properties = new FeatureProperties();
        featureFlagService = new FeatureFlagService(properties, null);
        interceptor = new FeatureToggleInterceptor(featureFlagService);
    }

    @Test
    @DisplayName("Disabled feature flag must throw FeatureDisabledException with 403 specification")
    void disabledFeatureFlagRejectsRequest() throws Exception {
        // By default "bills" is false
        assertThat(featureFlagService.isEnabled("bills")).isFalse();

        MockBillsController controller = new MockBillsController();
        HandlerMethod handlerMethod = new HandlerMethod(controller, controller.getClass().getMethod("getBills"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("The bills module is currently disabled in this environment.");
    }

    @Test
    @DisplayName("Enabled feature flag allows request to proceed")
    void enabledFeatureFlagAllowsRequest() throws Exception {
        featureFlagService.setFeature("bills", true, 1L, "127.0.0.1");
        assertThat(featureFlagService.isEnabled("bills")).isTrue();

        MockBillsController controller = new MockBillsController();
        HandlerMethod handlerMethod = new HandlerMethod(controller, controller.getClass().getMethod("getBills"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Non-annotated controller proceeds unconditionally")
    void nonAnnotatedControllerProceeds() throws Exception {
        MockPublicController controller = new MockPublicController();
        HandlerMethod handlerMethod = new HandlerMethod(controller, controller.getClass().getMethod("getPublic"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);
        assertThat(allowed).isTrue();
    }
}
