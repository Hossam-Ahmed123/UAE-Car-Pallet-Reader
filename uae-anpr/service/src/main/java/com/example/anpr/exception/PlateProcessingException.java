package com.example.anpr.exception;

public class PlateProcessingException extends RuntimeException {
    public PlateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
