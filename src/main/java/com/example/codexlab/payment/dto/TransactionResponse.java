package com.example.codexlab.payment.dto;

import com.example.codexlab.payment.entity.PaymentStatus;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Long paymentId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        Instant createdAt) {
}
