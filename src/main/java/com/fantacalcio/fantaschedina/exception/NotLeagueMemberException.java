package com.fantacalcio.fantaschedina.exception;

public class NotLeagueMemberException extends RuntimeException {

    public NotLeagueMemberException() {
        super("Non sei membro di questa lega");
    }
}
