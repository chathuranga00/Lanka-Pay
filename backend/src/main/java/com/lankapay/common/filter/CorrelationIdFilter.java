package com.lankapay.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a correlation ID to every request.
 *
 * - Reads X-Correlation-Id from the request header if present.
 * - Generates a UUID if not.
 * - Puts the ID into SLF4J MDC so it appears in every log line for this request.
 * - Writes the ID back in the response header so clients can trace calls.
 *
 * Order(1) ensures this runs before the Spring Security filter chain.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_HEADER  = "X-Correlation-Id";
    public static final String RESPONSE_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(REQUEST_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(RESPONSE_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clean up MDC to avoid leaking context between requests
            // (important in thread-pool environments)
            MDC.remove(MDC_KEY);
        }
    }
}
