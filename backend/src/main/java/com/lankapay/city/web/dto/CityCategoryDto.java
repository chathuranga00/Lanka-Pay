package com.lankapay.city.web.dto;

public record CityCategoryDto(
        String name,
        String displayName,
        String icon,
        int merchantCount
) {
}
