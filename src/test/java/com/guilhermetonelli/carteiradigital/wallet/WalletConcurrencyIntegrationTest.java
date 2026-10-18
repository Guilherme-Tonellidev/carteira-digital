package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.support.PostgresIntegrationTest;
import com.guilhermetonelli.carteiradigital.transaction.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Várias threads operando as mesmas carteiras ao mesmo tempo, contra um PostgreSQL de verdade. */
class WalletConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private record Outcome(Object value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    /** Todas as tarefas esperam um sinal de largada e disparam juntas. */
    private static List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 10));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (Throwable error) {
                        return new Outcome(null, error);
                    }
                }));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                // se houver deadlock, o timeout derruba o teste em vez de travar a execução
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("10 saques simultâneos de 20 numa carteira com 100: só 5 passam e o saldo nunca fica negativo")
    void simultaneousWithdrawalsNeverOverdraw() throws Exception {
        Wallet ana = newWallet("100.00");
        UUID anaUser = ownerOf(ana);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> walletService.withdraw(anaUser, ana.getId(), new BigDecimal("20.00"), null));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(Outcome::succeeded).count()).isEqualTo(5);
        assertThat(outcomes.stream().filter(o -> o.error() instanceof InsufficientBalanceException).count())
                .isEqualTo(5);
        assertThat(balanceOf(ana)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("20 transferências cruzadas simultâneas não travam e o total de dinheiro se mantém")
    void crossTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        Wallet ana = newWallet("100.00");
        Wallet bruno = newWallet("100.00");
        UUID anaUser = ownerOf(ana);
        UUID brunoUser = ownerOf(bruno);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> walletService.transfer(anaUser, ana.getId(), bruno.getId(), new BigDecimal("5.00"), null));
            tasks.add(() -> walletService.transfer(brunoUser, bruno.getId(), ana.getId(), new BigDecimal("5.00"), null));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(ana).add(balanceOf(bruno))).isEqualByComparingTo("200.00");
        assertThat(balanceOf(ana)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bruno)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("5 depósitos simultâneos com a mesma Idempotency-Key creditam uma vez só")
    void simultaneousDuplicatesWithSameKeyAreAppliedOnce() throws Exception {
        Wallet ana = newWallet("0");
        UUID anaUser = ownerOf(ana);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> walletService.deposit(anaUser, ana.getId(), new BigDecimal("50.00"), "mesma-chave"));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        long distinctTransactions = outcomes.stream()
                .map(outcome -> ((Transaction) outcome.value()).getId())
                .distinct()
                .count();
        assertThat(distinctTransactions).isEqualTo(1);
        assertThat(balanceOf(ana)).isEqualByComparingTo("50.00");
    }
}
