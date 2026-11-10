package com.example.codexlab.common.exception;

public class InvalidPaymentTransitionException extends RuntimeException {

    public InvalidPaymentTransitionException(String message) {
        super(message);
    }
}
