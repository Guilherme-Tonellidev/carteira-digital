package com.guilhermetonelli.carteiradigital.auth;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/** Extrai o id do usuário autenticado (o "subject" do JWT é o id do usuário). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
