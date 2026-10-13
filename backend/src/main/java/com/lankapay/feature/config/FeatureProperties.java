package com.lankapay.feature.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for Super App modular feature flags.
 * Defaults are safe: all new modules default to disabled.
 */
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureProperties {

    private String environment = "SANDBOX_SIMULATED";
    private String disclaimer = "All modules operate under simulated financial data. Non-payment banking services require licensed institutional partners.";
    private Map<String, Boolean> flags = new LinkedHashMap<>();

    public FeatureProperties() {
        // Safe default flags
        flags.put("stories", false);
        flags.put("bills", false);
        flags.put("scheduled", false);
        flags.put("split", false);
        flags.put("analytics", false);
        flags.put("rewards", false);
        flags.put("city", false);
        flags.put("support", false);
        flags.put("insights", false);
        flags.put("goals", false);
        flags.put("family", false);
        flags.put("investments", false);
        flags.put("borrowing", false);
        flags.put("insurance", false);
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, Boolean> flags) {
        if (flags != null) {
            this.flags.putAll(flags);
        }
    }
}
