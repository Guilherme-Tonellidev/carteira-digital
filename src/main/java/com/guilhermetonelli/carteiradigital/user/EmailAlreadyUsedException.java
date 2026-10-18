package com.guilhermetonelli.carteiradigital.user;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Já existe um usuário cadastrado com o e-mail " + email);
    }
}
