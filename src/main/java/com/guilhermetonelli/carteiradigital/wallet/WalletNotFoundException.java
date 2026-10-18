package com.guilhermetonelli.carteiradigital.wallet;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }

    public WalletNotFoundException(UUID id) {
        this("Carteira não encontrada: " + id);
    }
}
