package com.lankapay.split.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request to create a bill split")
public record CreateSplitRequest(
        @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must not exceed 100 characters")
        @Schema(description = "Title or description for the bill split", example = "Dinner at Ministry of Crab")
        String title,

        @NotNull(message = "totalMinor is required")
        @Positive(message = "totalMinor must be positive")
        @Schema(description = "Total bill amount in minor units (cents)", example = "900000")
        Long totalMinor,

        @Schema(description = "Currency code (default LKR)", example = "LKR")
        String currency,

        @Schema(description = "Split mode: EQUAL or CUSTOM (default EQUAL)", example = "EQUAL")
        String mode,

        @NotEmpty(message = "At least one participant is required")
        @Size(max = 10, message = "A maximum of 10 participants is allowed per split")
        @Schema(description = "List of participants in the split")
        List<SplitParticipantRequest> participants,

        @Schema(description = "Validity in hours (default 48)", example = "48")
        Integer validityHours
) {}
