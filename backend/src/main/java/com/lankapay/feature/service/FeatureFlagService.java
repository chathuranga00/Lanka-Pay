package com.lankapay.feature.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.feature.config.FeatureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe service for querying and updating feature flags.
 * Changes by administrators are audited.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final String environment;
    private final String disclaimer;
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();
    private final AuditService auditService;

    public FeatureFlagService(FeatureProperties properties, AuditService auditService) {
        this.environment = properties.getEnvironment() != null ? properties.getEnvironment() : "SANDBOX_SIMULATED";
        this.disclaimer = properties.getDisclaimer() != null
                ? properties.getDisclaimer()
                : "All modules operate under simulated financial data. Non-payment banking services require licensed institutional partners.";
        this.auditService = auditService;

        if (properties.getFlags() != null) {
            this.flags.putAll(properties.getFlags());
        }
        log.info("Initialized FeatureFlagService with {} feature flags: {}", flags.size(), flags);
    }

    public Map<String, Boolean> getAllFeatures() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(flags));
    }

    public boolean isEnabled(String featureKey) {
        if (featureKey == null) return false;
        return Boolean.TRUE.equals(flags.getOrDefault(featureKey.toLowerCase(), false));
    }

    public void setFeature(String featureKey, boolean enabled, Long adminUserId, String clientIp) {
        if (featureKey == null || featureKey.isBlank()) return;
        String key = featureKey.toLowerCase();
        boolean prev = isEnabled(key);
        flags.put(key, enabled);
        log.info("Feature flag [{}] changed from {} to {} by adminUserId={}", key, prev, enabled, adminUserId);

        if (auditService != null) {
            String sanitizedIp = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
            auditService.log(
                    adminUserId,
                    "FEATURE_FLAG_TOGGLED",
                    "FEATURE_FLAG",
                    null,
                    "{\"feature\":\"" + key + "\",\"previous\":" + prev + ",\"new\":" + enabled + "}",
                    sanitizedIp
            );
        }
    }

    public void setFeatures(Map<String, Boolean> updates, Long adminUserId, String clientIp) {
        if (updates == null || updates.isEmpty()) return;
        updates.forEach((key, val) -> setFeature(key, Boolean.TRUE.equals(val), adminUserId, clientIp));
    }

    public String getEnvironment() {
        return environment;
    }

    public String getDisclaimer() {
        return disclaimer;
    }
}
