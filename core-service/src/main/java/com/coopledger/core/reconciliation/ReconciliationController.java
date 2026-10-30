package com.coopledger.core.reconciliation;

import com.coopledger.shared.PageResponse;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reconciliation-runs")
@PreAuthorize("hasRole('OPS')")
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ReconciliationService.RunView> execute(Authentication auth) {
        var run = service.execute(auth.getName());
        return ResponseEntity.created(URI.create("/reconciliation-runs/" + run.id())).body(run);
    }

    @GetMapping("/{id}")
    public ReconciliationService.RunView find(@PathVariable UUID id) { return service.find(id); }

    @GetMapping("/{id}/divergences")
    public PageResponse<ReconciliationService.Divergence> divergences(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.divergences(id, page, size);
    }
}
