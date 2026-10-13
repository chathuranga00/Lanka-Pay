package com.lankapay.feature.exception;

/**
 * Thrown when a request accesses a feature/module that is currently disabled
 * by server-side feature flags.
 */
public class FeatureDisabledException extends RuntimeException {

    private final String feature;

    public FeatureDisabledException(String feature) {
        super("The " + feature + " module is currently disabled in this environment.");
        this.feature = feature;
    }

    public String getFeature() {
        return feature;
    }
}
