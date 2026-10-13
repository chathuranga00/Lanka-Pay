package com.lankapay.home.web;

import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.home.domain.HomeSection;
import com.lankapay.home.service.HomeAggregatorService;
import com.lankapay.home.web.dto.GreetingData;
import com.lankapay.home.web.dto.HomeAggregateResponse;
import com.lankapay.home.web.dto.NotificationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeController Web MVC and Resilience Tests")
class HomeControllerTest {

    @Mock
    private HomeAggregatorService homeAggregatorService;

    private MockMvc mockMvc;
    private static final Long CUSTOMER_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        HomeController controller = new HomeController(homeAggregatorService);

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
                String authHeader = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return CUSTOMER_USER_ID;
                }
                return null;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/home - unauthenticated request is rejected with 401 Unauthorized")
    void getHomeUnauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/home - authenticated customer receives complete resilient payload")
    void getHomeAuthenticatedReturnsAggregatedPayload() throws Exception {
        HomeAggregateResponse mockResponse = new HomeAggregateResponse(
                HomeSection.ok(new GreetingData("Kasun Perera", "K", "Good morning, Kasun")),
                HomeSection.ok(new NotificationData(3L)),
                HomeSection.failed("WALLET_UNAVAILABLE"), // tests that partial section failure does not break response
                HomeSection.ok(List.of()),
                HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED"),
                HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED"),
                HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED"),
                HomeSection.unavailable("MODULE_NOT_YET_IMPLEMENTED"),
                HomeSection.ok(List.of())
        );

        when(homeAggregatorService.aggregateHome(eq(CUSTOMER_USER_ID))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting.status").value("OK"))
                .andExpect(jsonPath("$.greeting.data.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.greeting.data.initial").value("K"))
                .andExpect(jsonPath("$.unreadNotifications.status").value("OK"))
                .andExpect(jsonPath("$.unreadNotifications.data.unreadCount").value(3))
                .andExpect(jsonPath("$.wallet.status").value("FAILED"))
                .andExpect(jsonPath("$.wallet.error").value("WALLET_UNAVAILABLE"))
                .andExpect(jsonPath("$.rewardsPoints.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.stories.status").value("UNAVAILABLE"));
    }
}
