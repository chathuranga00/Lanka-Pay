package com.lankapay.common.firestore;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Spring configuration for initializing FirebaseApp and Google Cloud Firestore.
 *
 * Initialization behavior:
 * 1. If FIRESTORE_EMULATOR_HOST is set (via env, system property, or app.firebase.emulator-host),
 *    connects to the local Firestore emulator using {@link NoCredentials}.
 * 2. If FIREBASE_CREDENTIALS_PATH is set and valid, initializes from the service account JSON.
 * 3. Otherwise attempts Application Default Credentials (ADC), falling back gracefully
 *    to offline development mode to avoid breaking Spring context startup when running with MySQL.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${app.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${app.firebase.project-id:lankapay-dev}")
    private String projectId;

    @Value("${app.firebase.emulator-host:}")
    private String configuredEmulatorHost;

    @Value("${app.persistence:mysql}")
    private String persistenceMode;

    @Bean
    public Firestore firestore() {
        String emulatorHost = getEffectiveEmulatorHost();

        if (emulatorHost != null && !emulatorHost.isBlank()) {
            log.info("Connecting to Firestore EMULATOR at host: [{}], projectId: [{}]", emulatorHost, projectId);
            System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);

            FirestoreOptions firestoreOptions = FirestoreOptions.getDefaultInstance().toBuilder()
                    .setProjectId(projectId)
                    .setHost(emulatorHost)
                    .setCredentials(NoCredentials.getInstance())
                    .build();

            initFirebaseAppWithDummyCredentials();
            return firestoreOptions.getService();
        }

        // Live / Cloud configuration
        if (credentialsPath != null && !credentialsPath.isBlank() && Files.exists(Paths.get(credentialsPath))) {
            try {
                log.info("Initializing Firebase from credentials file: [{}]", credentialsPath);
                try (InputStream is = Files.newInputStream(Paths.get(credentialsPath))) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(is);
                    initFirebaseApp(credentials);
                    return FirestoreClient.getFirestore();
                }
            } catch (Exception e) {
                log.error("Failed to load credentials from [{}]: {}", credentialsPath, e.getMessage());
                throw new IllegalStateException("Could not load Firebase credentials from path: " + credentialsPath, e);
            }
        }

        // Attempt ADC (Application Default Credentials)
        try {
            log.info("Attempting Application Default Credentials (ADC) for projectId: [{}]", projectId);
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            initFirebaseApp(credentials);
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            log.warn("ADC not available ({}); initializing Firestore with local NoCredentials (persistence mode: {})",
                    e.getMessage(), persistenceMode);

            FirestoreOptions fallbackOptions = FirestoreOptions.getDefaultInstance().toBuilder()
                    .setProjectId(projectId)
                    .setCredentials(NoCredentials.getInstance())
                    .build();

            initFirebaseAppWithDummyCredentials();
            return fallbackOptions.getService();
        }
    }

    private void initFirebaseApp(GoogleCredentials credentials) {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setProjectId(projectId)
                    .setCredentials(credentials)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp initialized successfully for project: [{}]", projectId);
        }
    }

    private void initFirebaseAppWithDummyCredentials() {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                GoogleCredentials dummy = GoogleCredentials.create(
                        new AccessToken("dummy-emulator-token", new Date(System.currentTimeMillis() + 86400000L))
                );
                FirebaseOptions options = FirebaseOptions.builder()
                        .setProjectId(projectId)
                        .setCredentials(dummy)
                        .build();
                FirebaseApp.initializeApp(options);
            } catch (Exception ignored) {
                // Ignore if dummy setup fails in pure client mode
            }
        }
    }

    private String getEffectiveEmulatorHost() {
        if (configuredEmulatorHost != null && !configuredEmulatorHost.isBlank()) {
            return configuredEmulatorHost;
        }
        String envHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }
        return System.getProperty("FIRESTORE_EMULATOR_HOST");
    }
}
