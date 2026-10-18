package com.guilhermetonelli.carteiradigital.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementEntry(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        StatementDirection direction,
        BigDecimal amount,
        UUID sourceWalletId,
        UUID targetWalletId,
        Instant createdAt) {
}
