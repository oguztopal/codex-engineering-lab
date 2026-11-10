package com.example.codexlab.common.api;

public record ApiError(String code, String message, Object details) {
}
