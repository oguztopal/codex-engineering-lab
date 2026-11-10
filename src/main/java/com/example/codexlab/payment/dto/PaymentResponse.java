package com.example.codexlab.payment.dto;

import com.example.codexlab.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long merchantId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
