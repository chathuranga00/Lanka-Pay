package com.lankapay.story.service;

import com.lankapay.common.exception.ValidationException;
import com.lankapay.story.domain.StoryPage;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates stories to guarantee safety:
 * - Strictly plain text (no raw HTML, script tags, or markup).
 * - Safe whitelisted deep-link schemes and destinations only.
 * - Allowlisted asset store prefixes for images.
 */
@Component
public class StoryValidator {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);

    private static final Set<String> ALLOWED_ROUTES = Set.of(
            "/accounts",
            "/accounts/add",
            "/cards",
            "/cards/add",
            "/cards/test-cards",
            "/settings",
            "/settings/privacy",
            "/settings/security",
            "/qr",
            "/history",
            "/rewards",
            "/bills",
            "/goals",
            "/send",
            "/explore",
            "/search",
            "/notifications"
    );

    private static final List<String> ALLOWED_ASSET_PREFIXES = List.of(
            "https://assets.lankapay.net/",
            "asset:",
            "local:",
            "gradient:"
    );

    public void validateTitleAndCaption(String title, String caption) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Story title must not be blank");
        }
        if (caption == null || caption.isBlank()) {
            throw new ValidationException("Story caption must not be blank");
        }
        validatePlainText("title", title);
        validatePlainText("caption", caption);
    }

    public void validatePages(List<StoryPage> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new ValidationException("Story must have at least one content page");
        }
        for (int i = 0; i < pages.size(); i++) {
            StoryPage page = pages.get(i);
            validatePlainText("page[" + i + "].text", page.text());
            if (page.actionLabel() != null && !page.actionLabel().isBlank()) {
                validatePlainText("page[" + i + "].actionLabel", page.actionLabel());
            }
            if (page.deepLink() != null && !page.deepLink().isBlank()) {
                validateDeepLink(page.deepLink());
            }
            if (page.imageRef() != null && !page.imageRef().isBlank()) {
                validateImageRef(page.imageRef());
            }
        }
    }

    public void validatePlainText(String fieldName, String value) {
        if (value == null) return;
        if (HTML_TAG_PATTERN.matcher(value).find()) {
            throw new ValidationException("Field '" + fieldName + "' contains forbidden HTML tags. Plain text only.");
        }
        if (value.toLowerCase().contains("javascript:") || value.toLowerCase().contains("<script")) {
            throw new ValidationException("Field '" + fieldName + "' contains suspicious script payloads.");
        }
    }

    public void validateDeepLink(String deepLink) {
        if (deepLink == null || deepLink.isBlank()) return;

        String lower = deepLink.trim().toLowerCase();

        // Reject arbitrary web URLs, javascript, and data schemes
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("javascript:") || lower.startsWith("data:") ||
                lower.startsWith("file:") || lower.startsWith("mailto:")) {
            throw new ValidationException("External or arbitrary URLs are not allowed. Whitelisted deep-links only: " + deepLink);
        }

        String routePath = deepLink.trim();
        if (routePath.startsWith("lankapay://")) {
            routePath = "/" + routePath.substring("lankapay://".length());
        }

        // Clean trailing slash and query params for route matching
        String cleanPath = routePath.split("\\?")[0];
        if (cleanPath.endsWith("/") && cleanPath.length() > 1) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }

        if (!ALLOWED_ROUTES.contains(cleanPath)) {
            throw new ValidationException("Deep-link '" + deepLink + "' is not in the whitelist of allowed internal routes.");
        }
    }

    public void validateImageRef(String imageRef) {
        if (imageRef == null || imageRef.isBlank()) return;

        String lower = imageRef.trim().toLowerCase();

        // Must not contain script or javascript
        if (lower.contains("javascript:") || lower.contains("<") || lower.contains(">")) {
            throw new ValidationException("Invalid imageRef contains suspicious characters");
        }

        boolean matched = false;
        for (String prefix : ALLOWED_ASSET_PREFIXES) {
            if (lower.startsWith(prefix)) {
                matched = true;
                break;
            }
        }

        if (!matched) {
            throw new ValidationException("imageRef '" + imageRef + "' must originate from an allowlisted asset store (e.g. https://assets.lankapay.net/ or asset:*)");
        }
    }
}
