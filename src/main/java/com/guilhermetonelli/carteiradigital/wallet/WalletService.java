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
    public Wallet getWallet(UUID walletId) {
        return findWallet(walletId);
    }

    /**
     * Se a mesma Idempotency-Key já foi usada com o mesmo valor, devolve a operação
     * original em vez de depositar de novo.
     */
    @Transactional
    public Transaction deposit(UUID walletId, BigDecimal amount, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        Wallet wallet = findWallet(walletId);

        Optional<Transaction> replay = findReplay(walletId, key, TransactionType.DEPOSIT, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        wallet.credit(amount);
        return transactions.save(Transaction.deposit(wallet, amount, key));
    }

    @Transactional
    public Transaction withdraw(UUID walletId, BigDecimal amount, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        Wallet wallet = findWallet(walletId);

        Optional<Transaction> replay = findReplay(walletId, key, TransactionType.WITHDRAWAL, amount);
        if (replay.isPresent()) {
            return replay.get();
        }

        wallet.debit(amount);
        return transactions.save(Transaction.withdrawal(wallet, amount, key));
    }

    private Wallet findWallet(UUID walletId) {
        return wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
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
