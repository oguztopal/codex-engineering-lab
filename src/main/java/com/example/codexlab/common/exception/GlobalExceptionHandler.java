package com.example.codexlab.common.exception;

import com.example.codexlab.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), null);
    }

    @ExceptionHandler(InvalidPaymentTransitionException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidTransition(InvalidPaymentTransitionException exception) {
        return error(HttpStatus.CONFLICT, "INVALID_PAYMENT_TRANSITION", exception.getMessage(), null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(OptimisticLockingFailureException exception) {
        return error(HttpStatus.CONFLICT, "PAYMENT_CONFLICT",
                "Payment was modified by another request; reload the payment before retrying", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data", null);
    }

    @ExceptionHandler(InvalidRefundAmountException.class)
    ResponseEntity<ApiResponse<Void>> handleRefundAmount(InvalidRefundAmountException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
                Map.of("amount", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(fieldError -> details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request could not be processed", null);
    }

    private ResponseEntity<ApiResponse<Void>> error(
            HttpStatus status, String code, String message, Object details) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, details));
    }
}
