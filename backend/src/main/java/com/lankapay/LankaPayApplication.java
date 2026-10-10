package com.lankapay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lanka Pay – entry point.
 *
 * NOTE: This is a university prototype.
 * All bank integrations are SIMULATED – no real money is moved.
 */
@SpringBootApplication
@EnableScheduling
public class LankaPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LankaPayApplication.class, args);
    }
}
