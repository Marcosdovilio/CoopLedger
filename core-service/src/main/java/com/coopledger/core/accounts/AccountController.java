package com.coopledger.core.accounts;

import com.coopledger.shared.PageResponse;
import com.coopledger.shared.security.Actor;
import com.coopledger.core.ledger.LedgerView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<AccountService.AccountView> create(@Valid @RequestBody CreateAccount request, Authentication auth) {
        var view = service.create(request.memberId(), Actor.from(auth));
        return ResponseEntity.created(URI.create("/accounts/" + view.id() + "/balance")).body(view);
    }

    @GetMapping("/{id}/balance")
    public AccountService.BalanceView balance(@PathVariable UUID id, Authentication auth) {
        return service.balance(id, Actor.from(auth));
    }

    @GetMapping("/{id}/statement")
    public PageResponse<LedgerView> statement(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication auth) {
        return service.statement(id, Actor.from(auth), page, size);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OPS')")
    public AccountService.AccountView status(@PathVariable UUID id, @Valid @RequestBody ChangeStatus request) {
        return service.changeStatus(id, request.status());
    }

    public record CreateAccount(@NotBlank @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9._@-]+") String memberId) { }
    public record ChangeStatus(@NotNull Account.Status status) { }
}
