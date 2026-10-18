package com.guilhermetonelli.carteiradigital.user;

import com.guilhermetonelli.carteiradigital.wallet.Wallet;
import com.guilhermetonelli.carteiradigital.wallet.WalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, WalletRepository wallets, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
    }

    /** Cadastra o usuário e já cria a carteira dele (saldo zero), na mesma transação. */
    @Transactional
    public Wallet register(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        User user = users.save(new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword)));
        return wallets.save(new Wallet(user));
    }
}
