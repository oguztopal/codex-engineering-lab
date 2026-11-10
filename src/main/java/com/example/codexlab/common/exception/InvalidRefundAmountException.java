package com.example.codexlab.common.exception;

public class InvalidRefundAmountException extends RuntimeException {

    public InvalidRefundAmountException(String message) {
        super(message);
    }
}
