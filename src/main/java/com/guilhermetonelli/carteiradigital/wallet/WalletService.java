package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.common.InvalidRequestException;
import com.guilhermetonelli.carteiradigital.transaction.IdempotencyKeyReuseException;
import com.guilhermetonelli.carteiradigital.transaction.Transaction;
import com.guilhermetonelli.carteiradigital.transaction.TransactionRepository;
import com.guilhermetonelli.carteiradigital.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Todas as operações recebem o id do usuário autenticado ({@code userId}) e só permitem
 * mexer em carteiras que pertencem a ele.
 */
@Service
public class WalletService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final WalletRepository wallets;
    private final TransactionRepository transactions;

    public WalletService(WalletRepository wallets, TransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Wallet getOwnWallet(UUID userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("Nenhuma carteira encontrada para o usuário autenticado"));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID userId, UUID walletId) {
        requireOwner(walletId, userId);
        return wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /**
     * A carteira é travada (FOR UPDATE) antes de qualquer verificação. Assim, duas requisições
     * simultâneas na mesma carteira são executadas uma de cada vez, e a segunda já enxerga o
     * resultado da primeira (inclusive uma Idempotency-Key repetida).
     */
    @Transactional
    public Transaction deposit(UUID userId, UUID walletId, BigDecimal amount, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        requireOwner(walletId, userId);
        Wallet wallet = lockWallet(walletId);

        Optional<Transaction> replay = findReplay(walletId, key, TransactionType.DEPOSIT, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        wallet.credit(amount);
        return transactions.save(Transaction.deposit(wallet, amount, key));
    }

    @Transactional
    public Transaction withdraw(UUID userId, UUID walletId, BigDecimal amount, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        requireOwner(walletId, userId);
        Wallet wallet = lockWallet(walletId);

        Optional<Transaction> replay = findReplay(walletId, key, TransactionType.WITHDRAWAL, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        wallet.debit(amount);
        return transactions.save(Transaction.withdrawal(wallet, amount, key));
    }

    /**
     * Debita a origem (que precisa ser do usuário autenticado) e credita o destino (qualquer
     * carteira existente) na mesma transação: ou acontecem os dois, ou nenhum.
     * A chave de idempotência pertence à carteira de origem.
     */
    @Transactional
    public Transaction transfer(UUID userId, UUID sourceId, UUID targetId, BigDecimal amount, String idempotencyKey) {
        if (sourceId.equals(targetId)) {
            throw new InvalidRequestException("A carteira de origem e a de destino devem ser diferentes");
        }
        String key = normalizeKey(idempotencyKey);

        // Confere o dono ANTES de travar, para quem não é dono não conseguir segurar o lock de uma carteira alheia.
        requireOwner(sourceId, userId);

        // Anti-deadlock: as duas carteiras são sempre travadas na mesma ordem (menor id primeiro),
        // não importa quem é origem ou destino. Sem isso, A->B e B->A ao mesmo tempo poderiam
        // travar uma à outra para sempre.
        UUID firstId = sourceId.compareTo(targetId) < 0 ? sourceId : targetId;
        UUID secondId = firstId.equals(sourceId) ? targetId : sourceId;
        Wallet first = lockWallet(firstId);
        Wallet second = lockWallet(secondId);
        Wallet source = firstId.equals(sourceId) ? first : second;
        Wallet target = firstId.equals(sourceId) ? second : first;

        Optional<Transaction> replay = findReplay(sourceId, key, TransactionType.TRANSFER, amount);
        if (replay.isPresent()) {
            Transaction existing = replay.get();
            if (!targetId.equals(existing.getTargetWallet().getId())) {
                throw new IdempotencyKeyReuseException();
            }
            return existing;
        }

        source.debit(amount);
        target.credit(amount);
        return transactions.save(Transaction.transfer(source, target, amount, key));
    }

    private void requireOwner(UUID walletId, UUID userId) {
        if (wallets.existsByIdAndUserId(walletId, userId)) {
            return;
        }
        if (!wallets.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        throw new WalletAccessDeniedException();
    }

    private Wallet lockWallet(UUID walletId) {
        return wallets.findByIdForUpdate(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private Optional<Transaction> findReplay(UUID walletId, String key, TransactionType type, BigDecimal amount) {
        if (key == null) {
            return Optional.empty();
        }
        return transactions.findByIdempotencyKey(walletId, key).map(existing -> {
            boolean sameOperation = existing.getType() == type && existing.getAmount().compareTo(amount) == 0;
            if (!sameOperation) {
                throw new IdempotencyKeyReuseException();
            }
            return existing;
        });
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException(
                    "Idempotency-Key deve ter no máximo " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        }
        return trimmed;
    }
}
