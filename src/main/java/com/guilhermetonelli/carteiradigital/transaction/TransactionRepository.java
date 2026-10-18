package com.guilhermetonelli.carteiradigital.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /**
     * Procura uma operação já feita com a mesma Idempotency-Key pela mesma carteira.
     * O COALESCE espelha o índice único uk_transactions_idempotency da migration V1.
     */
    @Query(value = """
            SELECT * FROM transactions
            WHERE idempotency_key = :key
              AND COALESCE(source_wallet_id, target_wallet_id) = :walletId
            """, nativeQuery = true)
    Optional<Transaction> findByIdempotencyKey(@Param("walletId") UUID walletId, @Param("key") String key);
}
