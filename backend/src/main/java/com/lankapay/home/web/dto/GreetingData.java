package com.lankapay.home.web.dto;

/**
 * Personalized greeting using the authenticated user's real registered name.
 * Never uses placeholder or mock names like "Mikhail".
 */
public record GreetingData(
        String fullName,
        String initial,
        String salutation
) {}
