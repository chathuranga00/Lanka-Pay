package com.lankapay.bank.web;

import com.lankapay.bank.domain.BankInstitution;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A bank institution available for account linking")
public record BankInstitutionResponse(

        @Schema(description = "Institution code used in link requests", example = "SIMULATED_BANK_A")
        String institutionCode,

        @Schema(description = "Display name", example = "Simulated Bank A [SIMULATED]")
        String displayName,

        @Schema(description = "True when this is a simulated institution")
        boolean simulated
) {
    public static BankInstitutionResponse from(BankInstitution i) {
        return new BankInstitutionResponse(i.getBankCode(), i.getDisplayName(), i.isSimulated());
    }
}
