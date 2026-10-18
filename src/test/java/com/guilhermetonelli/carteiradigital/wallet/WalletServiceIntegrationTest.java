package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.common.InvalidRequestException;
import com.guilhermetonelli.carteiradigital.support.PostgresIntegrationTest;
import com.guilhermetonelli.carteiradigital.transaction.IdempotencyKeyReuseException;
import com.guilhermetonelli.carteiradigital.transaction.StatementDirection;
import com.guilhermetonelli.carteiradigital.transaction.StatementEntry;
import com.guilhermetonelli.carteiradigital.transaction.Transaction;
import com.guilhermetonelli.carteiradigital.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private Page<StatementEntry> statement(Wallet wallet) {
        return walletService.getStatement(ownerOf(wallet), wallet.getId(), null, null, null, 0, 100);
    }

    // ---------------------------------------------------------------- depósito e saque

    @Test
    @DisplayName("depósito aumenta o saldo e registra a transação")
    void depositIncreasesBalanceAndRecordsTransaction() {
        Wallet ana = newWallet("0");

        Transaction transaction = walletService.deposit(ownerOf(ana), ana.getId(), money("100.50"), null);

        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("100.50");
        assertThat(balanceOf(ana)).isEqualByComparingTo("100.50");
    }

    @Test
    @DisplayName("saque diminui o saldo")
    void withdrawalDecreasesBalance() {
        Wallet ana = newWallet("100.00");

        walletService.withdraw(ownerOf(ana), ana.getId(), money("30.00"), null);

        assertThat(balanceOf(ana)).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("saque acima do saldo falha e não deixa rastro")
    void withdrawalAboveBalanceFailsWithoutSideEffects() {
        Wallet ana = newWallet("50.00");

        assertThatThrownBy(() -> walletService.withdraw(ownerOf(ana), ana.getId(), money("50.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("50.00");
        assertThat(statement(ana).getTotalElements()).isEqualTo(1); // só o depósito inicial
    }

    // ---------------------------------------------------------------- transferência

    @Test
    @DisplayName("transferência move o dinheiro e mantém o total")
    void transferMovesMoney() {
        Wallet ana = newWallet("100.00");
        Wallet bruno = newWallet("10.00");

        walletService.transfer(ownerOf(ana), ana.getId(), bruno.getId(), money("40.00"), null);

        assertThat(balanceOf(ana)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(bruno)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("transferência sem saldo não altera nenhuma das carteiras")
    void failedTransferChangesNothing() {
        Wallet ana = newWallet("10.00");
        Wallet bruno = newWallet("0");

        assertThatThrownBy(() -> walletService.transfer(ownerOf(ana), ana.getId(), bruno.getId(), money("10.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(bruno)).isEqualByComparingTo("0.00");
        assertThat(statement(bruno).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("transferir para a própria carteira é rejeitado")
    void transferToSameWalletIsRejected() {
        Wallet ana = newWallet("100.00");

        assertThatThrownBy(() -> walletService.transfer(ownerOf(ana), ana.getId(), ana.getId(), money("10.00"), null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("transferir para carteira inexistente falha e não debita a origem")
    void transferToUnknownWalletFails() {
        Wallet ana = newWallet("100.00");

        assertThatThrownBy(() -> walletService.transfer(ownerOf(ana), ana.getId(), UUID.randomUUID(), money("10.00"), null))
                .isInstanceOf(WalletNotFoundException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("100.00");
    }

    // ---------------------------------------------------------------- idempotência

    @Test
    @DisplayName("mesma Idempotency-Key devolve a operação original e credita uma vez só")
    void sameIdempotencyKeyIsAppliedOnce() {
        Wallet ana = newWallet("0");

        Transaction first = walletService.deposit(ownerOf(ana), ana.getId(), money("50.00"), "chave-1");
        Transaction second = walletService.deposit(ownerOf(ana), ana.getId(), money("50.00"), "chave-1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(balanceOf(ana)).isEqualByComparingTo("50.00");
        assertThat(statement(ana).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency-Key reutilizada com outro valor ou outra operação é rejeitada")
    void reusedKeyWithDifferentOperationIsRejected() {
        Wallet ana = newWallet("0");
        walletService.deposit(ownerOf(ana), ana.getId(), money("50.00"), "chave-2");

        assertThatThrownBy(() -> walletService.deposit(ownerOf(ana), ana.getId(), money("60.00"), "chave-2"))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        assertThatThrownBy(() -> walletService.withdraw(ownerOf(ana), ana.getId(), money("50.00"), "chave-2"))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("50.00");
    }

    // ---------------------------------------------------------------- dono da carteira

    @Test
    @DisplayName("ninguém mexe na carteira de outra pessoa")
    void onlyTheOwnerCanOperateTheWallet() {
        Wallet ana = newWallet("100.00");
        Wallet bruno = newWallet("0");

        assertThatThrownBy(() -> walletService.withdraw(ownerOf(bruno), ana.getId(), money("10.00"), null))
                .isInstanceOf(WalletAccessDeniedException.class);
        assertThatThrownBy(() -> walletService.deposit(ownerOf(bruno), ana.getId(), money("10.00"), null))
                .isInstanceOf(WalletAccessDeniedException.class);
        assertThatThrownBy(() -> walletService.transfer(ownerOf(bruno), ana.getId(), bruno.getId(), money("10.00"), null))
                .isInstanceOf(WalletAccessDeniedException.class);
        assertThatThrownBy(() -> walletService.getWallet(ownerOf(bruno), ana.getId()))
                .isInstanceOf(WalletAccessDeniedException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bruno)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("carteira inexistente dá not found, não acesso negado")
    void unknownWalletIsNotFound() {
        Wallet ana = newWallet("0");

        assertThatThrownBy(() -> walletService.getWallet(ownerOf(ana), UUID.randomUUID()))
                .isInstanceOf(WalletNotFoundException.class);
    }

    // ---------------------------------------------------------------- extrato

    @Test
    @DisplayName("extrato é paginado, do mais recente ao mais antigo, com direção CREDIT/DEBIT")
    void statementIsPaginatedNewestFirst() {
        Wallet ana = newWallet("0");
        Wallet bruno = newWallet("0");
        UUID anaUser = ownerOf(ana);
        walletService.deposit(anaUser, ana.getId(), money("100.00"), null);
        walletService.withdraw(anaUser, ana.getId(), money("30.00"), null);
        walletService.transfer(anaUser, ana.getId(), bruno.getId(), money("20.00"), null);

        Page<StatementEntry> firstPage = walletService.getStatement(anaUser, ana.getId(), null, null, null, 0, 2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(StatementEntry::type)
                .containsExactly(TransactionType.TRANSFER, TransactionType.WITHDRAWAL);
        assertThat(firstPage.getContent()).extracting(StatementEntry::direction)
                .containsExactly(StatementDirection.DEBIT, StatementDirection.DEBIT);

        Page<StatementEntry> secondPage = walletService.getStatement(anaUser, ana.getId(), null, null, null, 1, 2);
        assertThat(secondPage.getContent()).extracting(StatementEntry::type)
                .containsExactly(TransactionType.DEPOSIT);
        assertThat(secondPage.getContent()).extracting(StatementEntry::direction)
                .containsExactly(StatementDirection.CREDIT);

        Page<StatementEntry> brunoStatement = statement(bruno);
        assertThat(brunoStatement.getContent()).hasSize(1);
        StatementEntry received = brunoStatement.getContent().get(0);
        assertThat(received.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(received.direction()).isEqualTo(StatementDirection.CREDIT);
        assertThat(received.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("extrato filtra por tipo e por período")
    void statementFilters() {
        Wallet ana = newWallet("0");
        UUID anaUser = ownerOf(ana);
        walletService.deposit(anaUser, ana.getId(), money("100.00"), null);
        walletService.withdraw(anaUser, ana.getId(), money("30.00"), null);
        walletService.withdraw(anaUser, ana.getId(), money("10.00"), null);

        Page<StatementEntry> withdrawals = walletService.getStatement(
                anaUser, ana.getId(), TransactionType.WITHDRAWAL, null, null, 0, 20);
        assertThat(withdrawals.getTotalElements()).isEqualTo(2);

        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        assertThat(walletService.getStatement(anaUser, ana.getId(), null, today, today, 0, 20).getTotalElements())
                .isEqualTo(3);
        assertThat(walletService.getStatement(anaUser, ana.getId(), null, today.plusDays(1), null, 0, 20).getTotalElements())
                .isZero();
        assertThat(walletService.getStatement(anaUser, ana.getId(), null, null, today.minusDays(1), 0, 20).getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("extrato valida parâmetros e o dono da carteira")
    void statementValidatesInput() {
        Wallet ana = newWallet("0");
        Wallet bruno = newWallet("0");
        UUID anaUser = ownerOf(ana);
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        assertThatThrownBy(() -> walletService.getStatement(anaUser, ana.getId(), null, null, null, 0, 101))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> walletService.getStatement(anaUser, ana.getId(), null, null, null, -1, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> walletService.getStatement(anaUser, ana.getId(), null, today, today.minusDays(1), 0, 20))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> walletService.getStatement(anaUser, bruno.getId(), null, null, null, 0, 20))
                .isInstanceOf(WalletAccessDeniedException.class);
    }

    // ---------------------------------------------------------------- regras no próprio banco

    @Test
    @DisplayName("o banco bloqueia UPDATE e DELETE no histórico de transações")
    void databaseKeepsTransactionsImmutable() {
        Wallet ana = newWallet("10.00");
        UUID transactionId = statement(ana).getContent().get(0).id();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE transactions SET amount = 1 WHERE id = ?", transactionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("P0001"); // erro levantado pelo trigger

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM transactions WHERE id = ?", transactionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("P0001");
    }

    @Test
    @DisplayName("o banco recusa saldo negativo mesmo que o código falhe")
    void databaseRejectsNegativeBalance() {
        Wallet ana = newWallet("10.00");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE wallets SET balance = -1 WHERE id = ?", ana.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balanceOf(ana)).isEqualByComparingTo("10.00");
    }
}
