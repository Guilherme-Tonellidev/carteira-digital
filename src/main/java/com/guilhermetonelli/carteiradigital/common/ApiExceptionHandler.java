package com.guilhermetonelli.carteiradigital.common;

import com.guilhermetonelli.carteiradigital.auth.InvalidCredentialsException;
import com.guilhermetonelli.carteiradigital.transaction.IdempotencyKeyReuseException;
import com.guilhermetonelli.carteiradigital.user.EmailAlreadyUsedException;
import com.guilhermetonelli.carteiradigital.wallet.InsufficientBalanceException;
import com.guilhermetonelli.carteiradigital.wallet.WalletAccessDeniedException;
import com.guilhermetonelli.carteiradigital.wallet.WalletNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converte exceções em respostas de erro padronizadas (RFC 7807 / ProblemDetail). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleWalletNotFound(WalletNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Carteira não encontrada", ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Saldo insuficiente", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, "E-mail já cadastrado", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ProblemDetail handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency-Key reutilizada", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", ex.getMessage());
    }

    @ExceptionHandler(WalletAccessDeniedException.class)
    public ProblemDetail handleWalletAccessDenied(WalletAccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Operação concorrente",
                "A carteira foi alterada por outra operação ao mesmo tempo. Tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito de dados",
                "Os dados enviados conflitam com um registro já existente.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Um ou mais campos são inválidos.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Corpo inválido",
                "O corpo da requisição está ausente ou não é um JSON válido.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
