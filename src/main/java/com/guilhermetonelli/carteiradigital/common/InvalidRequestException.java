package com.guilhermetonelli.carteiradigital.common;

/** Requisição que viola uma regra de negócio simples (ex.: valor zero ou com mais de 2 casas decimais). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
