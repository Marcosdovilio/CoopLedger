package com.coopledger.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Profile("local")
@RestController
@RequestMapping("/auth")
public class DemoAuthController {
    private final JwtEncoder encoder;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final Map<String, DemoUser> users;

    public DemoAuthController(JwtEncoder encoder) {
        this.encoder = encoder;
        users = Map.of(
                "alice", new DemoUser("demo-alice", "MEMBER", passwords.encode("alice-demo")),
                "bob", new DemoUser("demo-bob", "MEMBER", passwords.encode("bob-demo")),
                "ops", new DemoUser("demo-ops", "OPS", passwords.encode("ops-demo")));
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody Login request) {
        DemoUser user = users.get(request.username());
        if (user == null || !passwords.matches(request.password(), user.password()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.subject()).issuer(SecurityConfiguration.ISSUER)
                .audience(List.of(SecurityConfiguration.AUDIENCE)).issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of(user.role())).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", 3600);
    }

    public record Login(@NotBlank String username, @NotBlank String password) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) { }
    private record DemoUser(String subject, String role, String password) { }
}
