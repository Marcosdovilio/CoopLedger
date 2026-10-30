package com.coopledger.operations.transfers;

import com.coopledger.shared.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations/transfers")
@PreAuthorize("hasRole('OPS')")
public class OperationsController {
    private final ProjectionRepository projections;
    public OperationsController(ProjectionRepository projections) { this.projections = projections; }

    @GetMapping
    public PageResponse<TransferProjection> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(projections.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("completedAt"), Sort.Order.desc("transferId")))));
    }

    @GetMapping("/{id}")
    public TransferProjection find(@PathVariable UUID id) {
        return projections.findById(id).orElseThrow(() -> new DomainException("TRANSFER_NOT_FOUND",
                "Transferência ainda não disponível na visão de operações."));
    }
}
