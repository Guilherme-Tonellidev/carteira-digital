package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.auth.CurrentUser;
import com.guilhermetonelli.carteiradigital.common.PageResponse;
import com.guilhermetonelli.carteiradigital.transaction.StatementEntry;
import com.guilhermetonelli.carteiradigital.transaction.TransactionResponse;
import com.guilhermetonelli.carteiradigital.transaction.TransactionType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** Carteira do usuário autenticado. */
    @GetMapping("/me")
    public WalletResponse me(Authentication authentication) {
        return WalletResponse.from(walletService.getOwnWallet(CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id, Authentication authentication) {
        return WalletResponse.from(walletService.getWallet(CurrentUser.id(authentication), id));
    }

    /** Extrato paginado. Exemplo: /wallets/{id}/transactions?type=DEPOSIT&from=2026-10-01&to=2026-10-31&page=0&size=20 */
    @GetMapping("/{id}/transactions")
    public PageResponse<StatementEntry> statement(
            @PathVariable UUID id,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return PageResponse.from(walletService.getStatement(
                CurrentUser.id(authentication), id, type, from, to, page, size));
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return TransactionResponse.from(
                walletService.deposit(CurrentUser.id(authentication), id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return TransactionResponse.from(
                walletService.withdraw(CurrentUser.id(authentication), id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return TransactionResponse.from(walletService.transfer(
                CurrentUser.id(authentication), id, request.targetWalletId(), request.amount(), idempotencyKey));
    }
}
