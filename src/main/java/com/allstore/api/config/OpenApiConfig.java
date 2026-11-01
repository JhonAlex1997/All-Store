package com.allstore.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
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

    private static final String CUALQUIERA = "*/*";

    /**
     * Springdoc documenta las respuestas como "*&#47;*", y el cliente que se genera para el front las
     * trataría como archivos. Aquí se declaran como JSON, salvo las que devuelven byte[] (PDF, XML,
     * CDR, Excel, archivos SIRE), que quedan como binario. Solo cambia la documentación, no las respuestas.
     */
    @Bean
    OpenApiCustomizer tiposDeRespuesta() {
        return openApi -> openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion ->
                operacion.getResponses().values().forEach(respuesta -> {
                    Content contenido = respuesta.getContent();
                    if (contenido == null || !contenido.containsKey(CUALQUIERA)) {
                        return;
                    }
                    MediaType tipo = contenido.remove(CUALQUIERA);
                    if (esBinario(tipo.getSchema())) {
                        contenido.addMediaType("application/octet-stream", new MediaType().schema(new BinarySchema()));
                    } else {
                        contenido.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, tipo);
                    }
                })));
    }

    private static boolean esBinario(Schema<?> esquema) {
        if (esquema == null) {
            return false;
        }
        // En OpenAPI 3.1 el tipo viene en getTypes(); en 3.0, en getType().
        boolean texto = "string".equals(esquema.getType())
                || (esquema.getTypes() != null && esquema.getTypes().contains("string"));
        return texto &&("byte".equals(esquema.getFormat()) || "binary".equals(esquema.getFormat()));
    }
}
