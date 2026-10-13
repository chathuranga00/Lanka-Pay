package com.lankapay.feature.interceptor;

import com.lankapay.feature.annotation.FeatureToggle;
import com.lankapay.feature.exception.FeatureDisabledException;
import com.lankapay.feature.service.FeatureFlagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HandlerInterceptor that intercepts requests and enforces server-side feature toggle
 * checks defined by @FeatureToggle on controller classes or handler methods.
 */
@Component
public class FeatureToggleInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeatureToggleInterceptor.class);

    private final FeatureFlagService featureFlagService;

    public FeatureToggleInterceptor(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        FeatureToggle toggle = handlerMethod.getMethodAnnotation(FeatureToggle.class);
        if (toggle == null) {
            toggle = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), FeatureToggle.class);
        }

        if (toggle != null) {
            String feature = toggle.value();
            if (!featureFlagService.isEnabled(feature)) {
                log.warn("Blocked request to disabled feature '{}' on URI {}", feature, request.getRequestURI());
                throw new FeatureDisabledException(feature);
            }
        }

        return true;
    }
}
