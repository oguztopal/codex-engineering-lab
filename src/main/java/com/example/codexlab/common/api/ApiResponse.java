package com.example.codexlab.common.api;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now());
    }
}
