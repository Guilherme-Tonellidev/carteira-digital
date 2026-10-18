package com.guilhermetonelli.carteiradigital.wallet;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(UUID id, BigDecimal balance) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance());
    }
}
