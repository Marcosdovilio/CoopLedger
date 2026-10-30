package com.coopledger.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    public static final String ISSUER = "coopledger";
    public static final String AUDIENCE = "coopledger-api";

    @Bean
    SecretKey jwtKey(@Value("${coopledger.security.secret}") String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET_BASE64 deve conter pelo menos 32 bytes.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência inválida", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), audience));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(key).build())));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper, Environment env,
            @Value("${management.server.port:9091}") int managementPort) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers(request -> request.getLocalPort() == managementPort
                            && request.getRequestURI().equals("/actuator/prometheus")).permitAll();
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    if (env.acceptsProfiles(Profiles.of("local"))) auth.requestMatchers(HttpMethod.POST, "/auth/token").permitAll();
                    auth.requestMatchers("/reconciliation-runs/**", "/operations/**", "/actuator/**").hasRole("OPS");
                    auth.requestMatchers(HttpMethod.PATCH, "/accounts/**").hasRole("OPS");
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setHeader("WWW-Authenticate", "Bearer");
                            res.setContentType("application/problem+json");
                            mapper.writeValue(res.getOutputStream(), Map.of("status", 401, "title", "Autenticação necessária", "code", "UNAUTHORIZED"));
                        }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/problem+json");
                    mapper.writeValue(res.getOutputStream(), Map.of("status", 403, "title", "Acesso negado", "code", "FORBIDDEN"));
                })).build();
    }
}
