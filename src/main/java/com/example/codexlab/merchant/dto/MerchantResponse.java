package com.example.codexlab.merchant.dto;

import java.time.Instant;

public record MerchantResponse(Long id, String name, String email, Instant createdAt) {
}
