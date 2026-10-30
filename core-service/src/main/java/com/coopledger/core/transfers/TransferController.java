package com.coopledger.core.transfers;

import com.coopledger.shared.security.Actor;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TransferView> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TransferRequest request, Authentication auth) {
        var result = service.transfer(request, key, Actor.from(auth));
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/transfers/" + result.transfer().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.transfer());
    }

    @GetMapping("/{id}")
    public TransferView find(@PathVariable UUID id, Authentication auth) {
        return service.find(id, Actor.from(auth));
    }
}
