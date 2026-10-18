package com.guilhermetonelli.carteiradigital.wallet;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(UUID id) {
        super("Carteira não encontrada: " + id);
    }
}
