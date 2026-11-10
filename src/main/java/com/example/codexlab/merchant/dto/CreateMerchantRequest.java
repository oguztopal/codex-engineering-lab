package com.example.codexlab.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email) {
}
