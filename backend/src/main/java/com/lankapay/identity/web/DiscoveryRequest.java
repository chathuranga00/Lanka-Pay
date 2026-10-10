package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to update the discovery (discoverability) flag")
public record DiscoveryRequest(

        @Schema(description = "true = phone is discoverable by others; false = opt out",
                example = "false")
        @NotNull(message = "discoverable is required")
        Boolean discoverable
) {}
