package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.common.InvalidRequestException;
import com.guilhermetonelli.carteiradigital.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    // Lock otimista: se duas requisições alterarem o mesmo saldo ao mesmo tempo,
    // a segunda falha com OptimisticLockingFailureException em vez de sobrescrever.
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Wallet() {
        // exigido pelo JPA
    }

    public Wallet(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Adiciona valor ao saldo. */
    public void credit(BigDecimal amount) {
        requireValidAmount(amount);
        this.balance = this.balance.add(amount);
    }

    /** Remove valor do saldo; nunca deixa o saldo ficar negativo. */
    public void debit(BigDecimal amount) {
        requireValidAmount(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(this.balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    private static void requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("O valor deve ser maior que zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("O valor deve ter no máximo 2 casas decimais");
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
