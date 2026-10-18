package com.guilhermetonelli.carteiradigital.wallet;

public class WalletAccessDeniedException extends RuntimeException {

    public WalletAccessDeniedException() {
        super("Você não tem permissão para operar esta carteira");
    }
}
