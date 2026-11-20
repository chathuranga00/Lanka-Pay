package com.lankapay.feature.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.feature.service.FeatureFlagService;
import com.lankapay.feature.web.dto.UpdateFeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureController and AdminFeatureController Web MVC Tests")
class FeatureControllerTest {

    @Mock
    private FeatureFlagService featureFlagService;

    private MockMvc publicMockMvc;
    private MockMvc adminMockMvc;
    private ObjectMapper objectMapper;

    private static final Long CUSTOMER_USER_ID = 1L;
    private static final Long ADMIN_USER_ID = 99L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        FeatureController featureController = new FeatureController(featureFlagService);
        AdminFeatureController adminFeatureController = new AdminFeatureController(featureFlagService);

        HandlerMethodArgumentResolver customerResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(Long.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String auth = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null && auth.startsWith("Bearer customer-")) {
                    return CUSTOMER_USER_ID;
                }
                return null;
            }
        };

        HandlerMethodArgumentResolver adminResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(Long.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String auth = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null && auth.startsWith("Bearer admin-")) {
                    return ADMIN_USER_ID;
                }
                return null;
            }
        };

        publicMockMvc = MockMvcBuilders.standaloneSetup(featureController)
                .setCustomArgumentResolvers(customerResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminFeatureController)
                .setCustomArgumentResolvers(adminResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/features - unauthenticated request is rejected with 401")
    void getFeaturesUnauthenticatedIsRejected() throws Exception {
        publicMockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/features - authenticated customer receives feature flags")
    void getFeaturesAuthenticatedCustomerSucceeds() throws Exception {
        when(featureFlagService.getAllFeatures()).thenReturn(Map.of("bills", false, "rewards", true));
        when(featureFlagService.getEnvironment()).thenReturn("SANDBOX_SIMULATED");
        when(featureFlagService.getDisclaimer()).thenReturn("Simulated Super App Environment");

        publicMockMvc.perform(get("/api/v1/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("SANDBOX_SIMULATED"))
                .andExpect(jsonPath("$.features.bills").value(false))
                .andExpect(jsonPath("$.features.rewards").value(true));
    }

    @Test
    @DisplayName("Admin feature endpoints - unauthenticated request is rejected with 401")
    void adminEndpointsRejectUnauthenticated() throws Exception {
        adminMockMvc.perform(get("/api/v1/admin/features"))
                .andExpect(status().isUnauthorized());

        adminMockMvc.perform(patch("/api/v1/admin/features/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeatureRequest(true))))
                .andExpect(status().isUnauthorized());

        adminMockMvc.perform(put("/api/v1/admin/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bills", true))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin can view, toggle, and bulk update feature flags")
    void adminCanToggleAndBulkUpdateFeatures() throws Exception {
        when(featureFlagService.getAllFeatures()).thenReturn(Map.of("bills", true, "stories", false));
        when(featureFlagService.getEnvironment()).thenReturn("SANDBOX_SIMULATED");
        when(featureFlagService.getDisclaimer()).thenReturn("Simulated Super App Environment");

        // 1. Admin views flags
        adminMockMvc.perform(get("/api/v1/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.bills").value(true));

        // 2. Admin toggles single flag
        adminMockMvc.perform(patch("/api/v1/admin/features/bills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeatureRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.bills").value(true));

        verify(featureFlagService).setFeature(eq("bills"), eq(true), eq(ADMIN_USER_ID), any());

        // 3. Admin bulk updates flags
        adminMockMvc.perform(put("/api/v1/admin/features")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bills", true, "rewards", false))))
                .andExpect(status().isOk());

        verify(featureFlagService).setFeatures(eq(Map.of("bills", true, "rewards", false)), eq(ADMIN_USER_ID), any());
    }
}
