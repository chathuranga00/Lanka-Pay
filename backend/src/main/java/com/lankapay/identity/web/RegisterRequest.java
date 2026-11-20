package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for new user registration")
public record RegisterRequest(

        @Schema(description = "Phone number in E.164 format", example = "+94771234567")
        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
        String phone,

        @Schema(description = "User's full name", example = "Kamal Perera")
        @NotBlank(message = "fullName is required")
        @Size(min = 2, max = 100, message = "fullName must be 2–100 characters")
        String fullName,

        @Schema(description = "National Identity Card number", example = "199012345678")
        @NotBlank(message = "nic is required")
        @Pattern(regexp = "^(\\d{9}[VvXx]|\\d{12})$",
                 message = "nic must be a valid Sri Lankan NIC (9 digits + V/X, or 12 digits)")
        String nic,

        @Schema(description = "PIN – 6 digits", example = "123456")
        @NotBlank(message = "pin is required")
        @Pattern(regexp = "^\\d{6}$", message = "pin must be exactly 6 digits")
        String pin,

        @Schema(description = "Role: CUSTOMER or MERCHANT", example = "CUSTOMER",
                allowableValues = {"CUSTOMER", "MERCHANT"})
        String role
) {
    /** Default to CUSTOMER when role is not supplied. */
    public String roleOrDefault() {
        return (role != null && role.equalsIgnoreCase("MERCHANT")) ? "MERCHANT" : "CUSTOMER";
    }
}
