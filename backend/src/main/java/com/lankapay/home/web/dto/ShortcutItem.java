package com.lankapay.home.web.dto;

public record ShortcutItem(
        String id,
        String label,
        String icon,
        String route,
        boolean enabled
) {}
