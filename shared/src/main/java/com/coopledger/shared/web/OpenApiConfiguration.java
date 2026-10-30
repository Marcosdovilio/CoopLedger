package com.coopledger.shared.web;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("CoopLedger API").version("v1")
                .description("Cooperativa fictícia · BRL · transferências internas sem tarifas ou cheque especial"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
