package com.example.codexlab.merchant.controller;

import com.example.codexlab.common.api.ApiResponse;
import com.example.codexlab.merchant.dto.CreateMerchantRequest;
import com.example.codexlab.merchant.dto.MerchantResponse;
import com.example.codexlab.merchant.service.MerchantService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    public ResponseEntity<ApiResponse<MerchantResponse>> create(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantResponse merchant = merchantService.create(request);
        return ResponseEntity.created(URI.create("/api/merchants/" + merchant.id()))
                .body(ApiResponse.success(merchant));
    }

    @GetMapping("/{id}")
    public ApiResponse<MerchantResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(merchantService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<MerchantResponse>> list() {
        return ApiResponse.success(merchantService.list());
    }
}
