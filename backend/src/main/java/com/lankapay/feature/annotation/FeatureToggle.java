package com.lankapay.feature.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation applied to Spring MVC controllers or handler methods to enforce
 * server-side feature toggle checks. If the specified feature is disabled,
 * requests are rejected with HTTP 403 Forbidden and errorCode FEATURE_DISABLED.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureToggle {

    /**
     * Feature flag key (e.g. "stories", "bills", "scheduled", "split", "analytics",
     * "rewards", "city", "support", "insights", "goals", "family").
     */
    String value();
}
