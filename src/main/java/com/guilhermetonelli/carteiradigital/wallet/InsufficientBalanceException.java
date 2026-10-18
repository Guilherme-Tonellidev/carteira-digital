package com.guilhermetonelli.carteiradigital.wallet;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(BigDecimal balance, BigDecimal requested) {
        super("Saldo insuficiente: saldo atual " + balance + ", valor solicitado " + requested);
    }
}
