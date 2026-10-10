package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment profile (registry entry) after phone is linked")
public record PaymentProfileResponse(

        @Schema(description = "Masked phone registered in the payment registry",
                example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "Whether this user's phone is discoverable by others",
                example = "true")
        boolean discoverable
) {}
