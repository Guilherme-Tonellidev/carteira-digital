package com.guilhermetonelli.carteiradigital.wallet;

import com.guilhermetonelli.carteiradigital.auth.CurrentUser;
import com.guilhermetonelli.carteiradigital.common.PageResponse;
import com.guilhermetonelli.carteiradigital.transaction.StatementEntry;
import com.guilhermetonelli.carteiradigital.transaction.TransactionResponse;
import com.guilhermetonelli.carteiradigital.transaction.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Carteiras", description = "Saldo, extrato, depósito, saque e transferência. Todas as rotas exigem token.")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private static final String IDEMPOTENCY_KEY_DESCRIPTION =
            "Chave opcional: reenviar a mesma operação com a mesma chave não duplica o efeito";

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    @Operation(summary = "Carteira e saldo do usuário autenticado")
    public WalletResponse me(@Parameter(hidden = true) Authentication authentication) {
        return WalletResponse.from(walletService.getOwnWallet(CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o saldo de uma carteira (somente a própria)")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "A carteira pertence a outro usuário"),
            @ApiResponse(responseCode = "404", description = "Carteira não encontrada")})
    public WalletResponse get(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return WalletResponse.from(walletService.getWallet(CurrentUser.id(authentication), id));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Extrato paginado, do mais recente para o mais antigo")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Parâmetro inválido (tipo, datas, page ou size)"),
            @ApiResponse(responseCode = "403", description = "A carteira pertence a outro usuário"),
            @ApiResponse(responseCode = "404", description = "Carteira não encontrada")})
    public PageResponse<StatementEntry> statement(
            @PathVariable UUID id,
            @Parameter(description = "Filtra por tipo de transação")
            @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Data inicial, inclusiva (AAAA-MM-DD, fuso de São Paulo)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final, inclusiva (AAAA-MM-DD, fuso de São Paulo)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Página, começando em 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) Authentication authentication) {
        return PageResponse.from(walletService.getStatement(
                CurrentUser.id(authentication), id, type, from, to, page, size));
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposita na própria carteira")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Valor inválido (zero, negativo ou mais de 2 casas decimais)"),
            @ApiResponse(responseCode = "403", description = "A carteira pertence a outro usuário"),
            @ApiResponse(responseCode = "404", description = "Carteira não encontrada"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key já usada em outra operação")})
    public TransactionResponse deposit(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) Authentication authentication) {
        return TransactionResponse.from(
                walletService.deposit(CurrentUser.id(authentication), id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Saca da própria carteira")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Valor inválido"),
            @ApiResponse(responseCode = "403", description = "A carteira pertence a outro usuário"),
            @ApiResponse(responseCode = "404", description = "Carteira não encontrada"),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou Idempotency-Key já usada em outra operação")})
    public TransactionResponse withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) Authentication authentication) {
        return TransactionResponse.from(
                walletService.withdraw(CurrentUser.id(authentication), id, request.amount(), idempotencyKey));
    }

    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfere da própria carteira para outra carteira")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Valor inválido ou origem igual ao destino"),
            @ApiResponse(responseCode = "403", description = "A carteira de origem pertence a outro usuário"),
            @ApiResponse(responseCode = "404", description = "Carteira de origem ou de destino não encontrada"),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou Idempotency-Key já usada em outra operação")})
    public TransactionResponse transfer(
            @PathVariable UUID id,
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) Authentication authentication) {
        return TransactionResponse.from(walletService.transfer(
                CurrentUser.id(authentication), id, request.targetWalletId(), request.amount(), idempotencyKey));
    }
}
