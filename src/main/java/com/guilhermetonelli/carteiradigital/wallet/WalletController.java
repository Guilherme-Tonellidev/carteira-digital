package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.transaction.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.from(walletService.getWallet(id));
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return TransactionResponse.from(walletService.deposit(id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return TransactionResponse.from(walletService.withdraw(id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return TransactionResponse.from(
                walletService.transfer(id, request.targetWalletId(), request.amount(), idempotencyKey));
    }
}
