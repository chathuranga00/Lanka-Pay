package com.lankapay.notification.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.domain.NotificationStatus;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.notification.web.dto.NotificationPreferenceResponse;
import com.lankapay.notification.web.dto.NotificationResponse;
import com.lankapay.notification.web.dto.UpdateNotificationPreferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService notificationService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        NotificationController controller = new NotificationController(notificationService);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
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
                return USER_ID;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications returns paginated notifications")
    void testGetNotifications() throws Exception {
        NotificationResponse item = new NotificationResponse(
                10L, NotificationChannelType.IN_APP, NotificationType.PAYMENT_RECEIVED,
                "Payment Received", "You received a payment from Nimal S. Open app to view details.",
                NotificationStatus.SENT, false, null, Instant.now(), true);

        when(notificationService.getUserNotifications(eq(USER_ID), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].title").value("Payment Received"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read marks notification as read")
    void testMarkAsRead() throws Exception {
        NotificationResponse item = new NotificationResponse(
                10L, NotificationChannelType.IN_APP, NotificationType.PAYMENT_RECEIVED,
                "Payment Received", "Message", NotificationStatus.SENT, true, Instant.now(), Instant.now(), true);

        when(notificationService.markAsRead(10L, USER_ID)).thenReturn(item);

        mockMvc.perform(patch("/api/v1/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/mark-all-read marks all as read")
    void testMarkAllAsRead() throws Exception {
        when(notificationService.markAllAsRead(USER_ID)).thenReturn(5);

        mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/preferences returns user preferences")
    void testGetPreferences() throws Exception {
        NotificationPreferenceResponse pref = new NotificationPreferenceResponse(
                true, true, true, true, true, true, Instant.now());

        when(notificationService.getPreferences(USER_ID)).thenReturn(pref);

        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inAppEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/preferences updates preferences")
    void testUpdatePreferences() throws Exception {
        UpdateNotificationPreferenceRequest req = new UpdateNotificationPreferenceRequest(
                true, false, true, false, true, true);

        NotificationPreferenceResponse pref = new NotificationPreferenceResponse(
                true, false, true, false, true, true, Instant.now());

        when(notificationService.updatePreferences(eq(USER_ID), any())).thenReturn(pref);

        mockMvc.perform(patch("/api/v1/notifications/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.emailEnabled").value(true));
    }
}
