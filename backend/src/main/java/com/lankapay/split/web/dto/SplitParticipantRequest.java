package com.lankapay.split.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Participant specification for a split")
public record SplitParticipantRequest(
        @Schema(description = "User ID of the participant (optional if phone provided)", example = "42")
        Long userId,

        @Schema(description = "Phone number of the participant (optional if userId provided)", example = "+94771234567")
        String phone,

        @Schema(description = "Custom share amount in minor currency (cents). Required if mode is CUSTOM.", example = "150000")
        Long shareMinor
) {}
