package com.allstore.api.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Configuración de seguridad ({@code app.seguridad.*} en application.properties).
 *
 * @param jwtSecret            clave para firmar los tokens (HS256); mínimo 32 bytes
 * @param expiracionMinutos    duración de una sesión antes de volver a ingresar
 * @param corsOrigenes         orígenes del frontend autorizados a llamar a la API
 * @param adminInicialPassword contraseña del primer administrador; si está vacía se genera una
 */
@ConfigurationProperties(prefix = "app.seguridad")
public record SeguridadProperties(
        String jwtSecret,
        @DefaultValue("480") long expiracionMinutos,
        @DefaultValue("http://localhost:4200") List<String> corsOrigenes,
        String adminInicialPassword
) {
    public static final int LONGITUD_MINIMA_SECRETO = 32;

    /** Falla al arrancar con un mensaje claro en lugar de emitir tokens con una clave débil. */
    public SeguridadProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("Falta JWT_SECRET o es muy corto: define una clave aleatoria de al menos "
                    + LONGITUD_MINIMA_SECRETO + " caracteres (variable de entorno JWT_SECRET o en el .env)");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException("app.seguridad.expiracion-minutos debe ser mayor que 0");
        }
    }
}
