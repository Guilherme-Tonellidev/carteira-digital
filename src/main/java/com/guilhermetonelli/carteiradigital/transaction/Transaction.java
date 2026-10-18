package com.guilhermetonelli.carteiradigital.transaction;

import com.guilhermetonelli.carteiradigital.wallet.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma movimentação financeira. Depois de gravada nunca é alterada
 * (o banco também bloqueia UPDATE/DELETE por trigger).
 */
@Entity
@Immutable
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id", updatable = false)
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_wallet_id", updatable = false)
    private Wallet targetWallet;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // exigido pelo JPA
    }

    private Transaction(TransactionType type, Wallet sourceWallet, Wallet targetWallet,
                        BigDecimal amount, String idempotencyKey) {
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.sourceWallet = sourceWallet;
        this.targetWallet = targetWallet;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public static Transaction deposit(Wallet target, BigDecimal amount, String idempotencyKey) {
        return new Transaction(TransactionType.DEPOSIT, null, target, amount, idempotencyKey);
    }

    public static Transaction withdrawal(Wallet source, BigDecimal amount, String idempotencyKey) {
        return new Transaction(TransactionType.WITHDRAWAL, source, null, amount, idempotencyKey);
    }

    public static Transaction transfer(Wallet source, Wallet target, BigDecimal amount, String idempotencyKey) {
        return new Transaction(TransactionType.TRANSFER, source, target, amount, idempotencyKey);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Wallet getSourceWallet() {
        return sourceWallet;
    }

    public Wallet getTargetWallet() {
        return targetWallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
