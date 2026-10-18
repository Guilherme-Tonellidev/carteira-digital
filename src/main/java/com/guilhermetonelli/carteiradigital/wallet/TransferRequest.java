package com.guilhermetonelli.carteiradigital.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "A carteira de destino é obrigatória")
        UUID targetWalletId,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor mínimo é 0,01")
        @Digits(integer = 17, fraction = 2, message = "Use no máximo 2 casas decimais")
        BigDecimal amount) {
}
