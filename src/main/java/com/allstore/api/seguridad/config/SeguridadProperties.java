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
 * @param intentosLoginPorIp   ingresos fallidos que se toleran desde una misma IP en la ventana; al
 *                             llegar, esa IP espera (protege del password spraying: una clave contra
 *                             muchos usuarios, que el bloqueo por usuario no detecta)
 * @param ventanaLoginMinutos  ventana de esos intentos
 * @param maximoCuerpoKb       tamaño máximo del cuerpo de una solicitud (protege la memoria)
 * @param adminInicialPassword contraseña del primer administrador; si está vacía se genera una
 * @param metricasToken        clave con que Prometheus lee /actuator/prometheus (usuario "metricas");
 *                             vacía = las métricas no se pueden leer desde fuera
 */
@ConfigurationProperties(prefix = "app.seguridad")
public record SeguridadProperties(
        String jwtSecret,
        @DefaultValue("480") long expiracionMinutos,
        @DefaultValue("http://localhost:4200") List<String> corsOrigenes,
        @DefaultValue("20") int intentosLoginPorIp,
        @DefaultValue("15") int ventanaLoginMinutos,
        @DefaultValue("1024") int maximoCuerpoKb,
        String adminInicialPassword,
        String metricasToken
) {
    public static final int LONGITUD_MINIMA_SECRETO = 32;

    /** Falla al arrancar con un mensaje claro en lugar de emitir tokens con una clave débil. */
    public SeguridadProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("Falta JWT_SECRET o es muy corto: define una clave aleatoria de al menos "
                    + LONGITUD_MINIMA_SECRETO + " caracteres (variable de entorno JWT_SECRET o en el .env)");
        }
        if (metricasToken != null && !metricasToken.isBlank() && metricasToken.length() < 16) {
            throw new IllegalStateException("METRICAS_TOKEN debe tener al menos 16 caracteres (usa uno aleatorio)");
        }
        if (expiracionMinutos <= 0) {
            throw new IllegalStateException("app.seguridad.expiracion-minutos debe ser mayor que 0");
        }
    }
}
