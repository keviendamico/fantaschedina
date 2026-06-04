package com.fantacalcio.fantaschedina.exception;

import lombok.Getter;

@Getter
public class RegistrationException extends RuntimeException {

    private final String token;

    public RegistrationException(String message, String token) {
        super(message);
        this.token = token;
    }
}
