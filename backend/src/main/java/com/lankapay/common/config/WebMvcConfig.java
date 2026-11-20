package com.lankapay.common.config;

import com.lankapay.feature.interceptor.FeatureToggleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web MVC Configuration.
 * Registers interceptors such as FeatureToggleInterceptor for server-side
 * feature flag enforcement across all incoming API requests.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FeatureToggleInterceptor featureToggleInterceptor;

    public WebMvcConfig(FeatureToggleInterceptor featureToggleInterceptor) {
        this.featureToggleInterceptor = featureToggleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureToggleInterceptor)
                .addPathPatterns("/api/**");
    }
}
