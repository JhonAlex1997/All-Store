package com.allstore.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Agrega el botón "Authorize" en Swagger: se pega el accessToken de /api/auth/login y Swagger
 * lo envía en cada petición.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "AllStore API", version = "v1",
                description = "Facturación y ERP para tienda de jeans. Inicia sesión en POST /api/auth/login "
                        + "y pega el accessToken en Authorize."),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
