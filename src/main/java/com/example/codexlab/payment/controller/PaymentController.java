package com.example.codexlab.payment.controller;

import com.example.codexlab.common.api.ApiResponse;
import com.example.codexlab.common.api.PageResponse;
import com.example.codexlab.payment.dto.CreatePaymentRequest;
import com.example.codexlab.payment.dto.PaymentResponse;
import com.example.codexlab.payment.dto.RefundPaymentRequest;
import com.example.codexlab.payment.dto.TransactionResponse;
import com.example.codexlab.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse payment = paymentService.create(request);
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id()))
                .body(ApiResponse.success(payment));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(paymentService.getById(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(paymentService.list(page, size));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<PaymentResponse> complete(@PathVariable Long id) {
        return ApiResponse.success(paymentService.complete(id));
    }

    @GetMapping("/{id}/transactions")
    public ApiResponse<List<TransactionResponse>> history(@PathVariable Long id) {
        return ApiResponse.success(paymentService.getHistory(id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<PaymentResponse> refund(
            @PathVariable Long id, @Valid @RequestBody RefundPaymentRequest request) {
        return ApiResponse.success(paymentService.refund(id, request));
    }
}
