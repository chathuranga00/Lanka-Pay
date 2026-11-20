package com.lankapay.home.domain;

/**
 * Resilient container for an individual dashboard section.
 * Guarantees zero cascading failures: if an individual section fails,
 * it returns status=FAILED with an error code and data=null, allowing
 * the rest of the Home screen to render smoothly.
 */
public record HomeSection<T>(
        HomeSectionStatus status,
        T data,
        String error
) {
    public static <T> HomeSection<T> ok(T data) {
        return new HomeSection<>(HomeSectionStatus.OK, data, null);
    }

    public static <T> HomeSection<T> failed(String error) {
        return new HomeSection<>(HomeSectionStatus.FAILED, null, error != null ? error : "SECTION_FAILED");
    }

    public static <T> HomeSection<T> unavailable(String message) {
        return new HomeSection<>(HomeSectionStatus.UNAVAILABLE, null, message != null ? message : "NOT_AVAILABLE");
    }
}
