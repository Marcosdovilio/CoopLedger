package com.coopledger.shared.web;

import com.coopledger.shared.DomainException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException ex) {
        HttpStatus status = switch (ex.code()) {
            case "ACCOUNT_NOT_FOUND", "TRANSFER_NOT_FOUND", "RUN_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "IDEMPOTENCY_CONFLICT", "ACCOUNT_BLOCKED", "INSUFFICIENT_FUNDS", "BALANCE_LIMIT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso não autorizado.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Verifique os campos informados.");
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()))).toList());
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail invalidRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Corpo, identificador ou paginação inválidos.");
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    ProblemDetail busy(CannotAcquireLockException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_OPERATION", "Operação concorrente. Tente novamente com a mesma chave.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.error("Violação de integridade na persistência", ex);
        return problem(HttpStatus.CONFLICT, "INTEGRITY_CONFLICT", "A operação viola uma restrição de integridade.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        return detail;
    }
}
