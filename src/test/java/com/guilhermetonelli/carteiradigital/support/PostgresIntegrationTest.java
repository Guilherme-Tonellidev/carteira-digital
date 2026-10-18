package com.guilhermetonelli.carteiradigital.support;

import com.guilhermetonelli.carteiradigital.user.UserService;
import com.guilhermetonelli.carteiradigital.wallet.Wallet;
import com.guilhermetonelli.carteiradigital.wallet.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base dos testes de integração: sobe a aplicação inteira contra um PostgreSQL real (em Docker).
 * As migrations do Flyway rodam de verdade, então o schema, os CHECKs e o trigger também são testados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationTest {

    // Um único container para todas as classes de teste (o Testcontainers o remove ao final da execução).
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected UserService userService;

    @Autowired
    protected WalletService walletService;

    /** Cria um usuário novo (e-mail único) com carteira e, se informado, um saldo inicial. */
    protected Wallet newWallet(String initialBalance) {
        Wallet wallet = userService.register("Teste", UUID.randomUUID() + "@teste.com", "senha1234");
        BigDecimal initial = new BigDecimal(initialBalance);
        if (initial.signum() > 0) {
            walletService.deposit(ownerOf(wallet), wallet.getId(), initial, null);
        }
        return wallet;
    }

    protected static UUID ownerOf(Wallet wallet) {
        return wallet.getUser().getId();
    }

    protected BigDecimal balanceOf(Wallet wallet) {
        return walletService.getWallet(ownerOf(wallet), wallet.getId()).getBalance();
    }
}
