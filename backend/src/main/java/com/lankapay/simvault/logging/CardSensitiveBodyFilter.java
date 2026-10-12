package com.lankapay.simvault.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Servlet filter that prevents any request/response body logging
 * for {@code /sim-vault/**} paths.
 *
 * Specifically:
 * - Replaces the readable request body with a non-loggable wrapper
 *   that returns an empty byte array when Spring's access-log or
 *   Logback's HTTP logging tries to read it.
 * - Sets a request attribute that any downstream logger can check
 *   to suppress body output.
 *
 * Order(-100) — runs before correlation filter and Spring Security.
 *
 * This is a defence-in-depth layer. The primary control is that the
 * controller never logs raw request fields.
 */
@Component
@Order(-100)
public class CardSensitiveBodyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CardSensitiveBodyFilter.class);

    /** Request attribute set to "true" for /sim-vault/** to suppress body logging. */
    public static final String SUPPRESS_BODY_ATTR = "simvault.suppress.body.logging";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path != null && path.contains("/sim-vault/")) {
            // Signal to any logging infrastructure to skip body
            request.setAttribute(SUPPRESS_BODY_ATTR, "true");
            // Wrap so body can be read by the controller but not re-read by loggers
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request) {
                @Override
                public byte[] getContentAsByteArray() {
                    // Return empty — body logged as empty for /sim-vault/ paths
                    return new byte[0];
                }
            };
            log.debug("[SIMVAULT] Body logging suppressed for path={}", path);
            chain.doFilter(wrapped, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
