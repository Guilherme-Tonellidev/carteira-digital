package com.guilhermetonelli.carteiradigital.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Faça login em POST /auth/login e cole aqui apenas o accessToken (sem a palavra Bearer).")
public class OpenApiConfig {

    @Bean
    public OpenAPI carteiraDigitalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Carteira Digital API")
                .description("API REST de carteira digital: cadastro, login, depósito, saque, transferência e extrato.")
                .version("v1"));
    }
}
