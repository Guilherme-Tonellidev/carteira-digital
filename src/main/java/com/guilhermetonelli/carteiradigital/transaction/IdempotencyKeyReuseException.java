package com.guilhermetonelli.carteiradigital.transaction;

public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("Esta Idempotency-Key já foi usada em outra operação ou com outro valor.");
    }
}
