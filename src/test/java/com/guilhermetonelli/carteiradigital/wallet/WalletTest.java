package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.common.InvalidRequestException;
import com.guilhermetonelli.carteiradigital.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de saldo da carteira, sem Spring e sem banco. */
class WalletTest {

    private static Wallet newWallet() {
        return new Wallet(new User("Ana", "ana@email.com", "hash"));
    }

    @Test
    @DisplayName("nasce com saldo zero")
    void startsWithZeroBalance() {
        assertThat(newWallet().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("credit soma ao saldo")
    void creditIncreasesBalance() {
        Wallet wallet = newWallet();

        wallet.credit(new BigDecimal("10.50"));
        wallet.credit(new BigDecimal("0.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("10.75");
    }

    @Test
    @DisplayName("debit subtrai do saldo, inclusive o saldo inteiro")
    void debitDecreasesBalance() {
        Wallet wallet = newWallet();
        wallet.credit(new BigDecimal("100.00"));

        wallet.debit(new BigDecimal("30.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("70.00");

        wallet.debit(new BigDecimal("70.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("debit acima do saldo falha e não altera o saldo")
    void debitAboveBalanceFailsAndKeepsBalance() {
        Wallet wallet = newWallet();
        wallet.credit(new BigDecimal("50.00"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    @DisplayName("valores zero ou negativos são rejeitados")
    void rejectsNonPositiveAmounts(String value) {
        Wallet wallet = newWallet();
        wallet.credit(new BigDecimal("100.00"));

        assertThatThrownBy(() -> wallet.credit(new BigDecimal(value)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal(value)))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("valor nulo é rejeitado")
    void rejectsNullAmount() {
        Wallet wallet = newWallet();

        assertThatThrownBy(() -> wallet.credit(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> wallet.debit(null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("mais de 2 casas decimais é rejeitado, mas zeros à direita são aceitos")
    void validatesDecimalPlaces() {
        Wallet wallet = newWallet();

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("10.001")))
                .isInstanceOf(InvalidRequestException.class);

        wallet.credit(new BigDecimal("10.500"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.50");
    }
}
