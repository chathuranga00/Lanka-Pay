package com.lankapay.identity.otp;

import com.lankapay.identity.domain.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TEST OTP provider — active when {@code OTP_MODE=TEST} (the default).
 *
 * ⚠ UNIVERSITY PROTOTYPE — no real SMS is ever sent.
 *
 * Behaviour:
 *  1. Prints the OTP clearly to the console (easy to see in terminal).
 *  2. Writes the OTP to  otp-dev.txt  in the project root so you can
 *     open that file any time to find your latest code.
 *
 * otp-dev.txt location:  q:\MY Projects\Lanka Pay\otp-dev.txt
 *
 * The file is overwritten on every new OTP so it always shows the latest one.
 * It is excluded from git via .gitignore (contains no real secrets in TEST mode).
 *
 * NEVER activate in production — switch OTP_MODE=PROD for real SMS.
 */
@Component
@ConditionalOnProperty(name = "app.otp.mode", havingValue = "TEST", matchIfMissing = true)
public class TestOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(TestOtpProvider.class);

    // Written to the project root (two levels up from backend/)
    private static final Path OTP_FILE = Paths.get(
            System.getProperty("user.dir")).getParent().resolve("otp-dev.txt");

    @Override
    public void send(String e164Phone, String maskedPhone, String plainOtp,
                     OtpPurpose purpose, long expiryMinutes) {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // ── 1. Console output (loud and visible) ──────────────
        System.out.println();
        System.out.println("┌─────────────────────────────────────────┐");
        System.out.println("│          YOUR OTP CODE (TEST MODE)       │");
        System.out.println("│                                          │");
        System.out.printf( "│   CODE  :  %-6s                        │%n", plainOtp);
        System.out.printf( "│   Phone :  %-20s          │%n", maskedPhone);
        System.out.printf( "│   Valid :  %d minutes                      │%n", expiryMinutes);
        System.out.println("│                                          │");
        System.out.println("│   Also saved to:  otp-dev.txt            │");
        System.out.println("└─────────────────────────────────────────┘");
        System.out.println();

        // ── 2. Write to otp-dev.txt ────────────────────────────
        try {
            String content = String.format(
                    """
                    ╔══════════════════════════════════════╗
                    ║       LANKA PAY — TEST OTP CODE      ║
                    ╠══════════════════════════════════════╣
                    ║                                      ║
                    ║   YOUR CODE :  %s                 ║
                    ║                                      ║
                    ║   Phone     :  %s
                    ║   Purpose   :  %s
                    ║   Generated :  %s
                    ║   Valid for :  %d minutes
                    ║                                      ║
                    ║   Enter this code in the app now.    ║
                    ╚══════════════════════════════════════╝
                    """,
                    plainOtp, maskedPhone, purpose, timestamp, expiryMinutes);

            Files.writeString(OTP_FILE, content);
            log.info("[TEST OTP] Code written to: {}", OTP_FILE.toAbsolutePath());

        } catch (IOException ex) {
            // File write failing must never break the OTP flow
            log.warn("[TEST OTP] Could not write otp-dev.txt: {}", ex.getMessage());
        }
    }
}
