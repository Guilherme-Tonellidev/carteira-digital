package com.guilhermetonelli.carteiradigital.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        // mesma mensagem para e-mail inexistente e senha errada, para não revelar quais e-mails existem
        super("E-mail ou senha inválidos");
    }
}
