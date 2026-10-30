package com.coopledger.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public record Actor(String id, boolean operations) {
    public static Actor from(Authentication auth) {
        return new Actor(auth.getName(), auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OPS")));
    }
    public void requireOwnerOrOperations(String ownerId) {
        if (!operations && !id.equals(ownerId)) throw new AccessDeniedException("Acesso à conta não autorizado.");
    }
}
