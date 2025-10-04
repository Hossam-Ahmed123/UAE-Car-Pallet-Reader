package com.example.anpr.exception;

public class PlateNotFoundException extends RuntimeException {
    public PlateNotFoundException(String message) {
        super(message);
    }
}
