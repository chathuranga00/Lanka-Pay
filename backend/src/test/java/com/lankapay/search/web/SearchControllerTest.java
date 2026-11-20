package com.lankapay.search.web;

import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.search.domain.SearchResultType;
import com.lankapay.search.service.SearchRateLimiter;
import com.lankapay.search.service.SearchService;
import com.lankapay.search.web.dto.SearchResponse;
import com.lankapay.search.web.dto.SearchResultItem;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController Web MVC, Rate Limiting, and Validation Tests")
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SearchRateLimiter searchRateLimiter;

    private MockMvc mockMvc;
    private static final Long CUSTOMER_USER_ID = 101L;

    @BeforeEach
    void setUp() {
        SearchController controller = new SearchController(searchService, searchRateLimiter);

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
    @DisplayName("GET /api/v1/search - Unauthenticated request is rejected with 401 Unauthorized")
    void searchUnauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "electricity"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(searchRateLimiter);
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("GET /api/v1/search - Query shorter than 2 characters is rejected with 400 Bad Request")
    void searchShortQueryRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "a")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_QUERY_TOO_SHORT"));

        verifyNoInteractions(searchRateLimiter);
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("GET /api/v1/search - Rate limit exceeded returns 429 Too Many Requests")
    void searchRateLimitExceededReturns429() throws Exception {
        doThrow(new RateLimitException("SEARCH_RATE_LIMITED", "Too many search requests"))
                .when(searchRateLimiter).consume(eq(CUSTOMER_USER_ID), anyString());

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "electricity")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SEARCH_RATE_LIMITED"));

        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("GET /api/v1/search - Valid query returns 200 OK with flat and grouped results")
    void searchValidQueryReturns200() throws Exception {
        SearchResultItem item = new SearchResultItem(
                "biller-ceb",
                SearchResultType.BILLER,
                "CEB",
                "Electricity • CEB",
                "BILLER",
                "/bills/ceb",
                "receipt"
        );
        SearchResponse mockResponse = new SearchResponse(
                "ceb",
                1,
                List.of(item),
                Map.of(SearchResultType.BILLER, List.of(item))
        );

        when(searchService.search(eq(CUSTOMER_USER_ID), eq("ceb"), anySet(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "ceb")
                        .param("types", "BILLER,MERCHANT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("ceb"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.results[0].title").value("CEB"))
                .andExpect(jsonPath("$.groupedResults.BILLER[0].id").value("biller-ceb"));

        verify(searchRateLimiter).consume(eq(CUSTOMER_USER_ID), anyString());
    }

    @Test
    @DisplayName("GET /api/v1/search - Phone number query returns Action without user enumeration")
    void searchPhoneNumberReturnsActionWithoutEnumeration() throws Exception {
        SearchResultItem actionItem = new SearchResultItem(
                "action-send-0771234567",
                SearchResultType.ACTION,
                "Send to this number",
                "0771234567",
                "PAY",
                "app://send?recipient=0771234567",
                "send"
        );
        SearchResponse mockResponse = new SearchResponse(
                "0771234567",
                1,
                List.of(actionItem),
                Map.of(SearchResultType.ACTION, List.of(actionItem))
        );

        when(searchService.search(eq(CUSTOMER_USER_ID), eq("0771234567"), anySet(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "0771234567")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].type").value("ACTION"))
                .andExpect(jsonPath("$.results[0].title").value("Send to this number"))
                .andExpect(jsonPath("$.results[0].deepLink").value("app://send?recipient=0771234567"));
    }
}
