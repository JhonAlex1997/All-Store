package com.allstore.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "limaDateTimeProvider", auditorAwareRef = "usuarioActual")
public class JpaAuditingConfig {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    /** Autor de los cambios hechos por procesos sin usuario (arranque, datos demo, tareas). */
    public static final String USUARIO_SISTEMA = "sistema";

    // OffsetDateTime no es soportado por el proveedor por defecto (usa LocalDateTime), así que
    // se define uno explícito anclado a la hora de Perú.
    @Bean
    public DateTimeProvider limaDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZONA_LIMA));
    }

    /** Llena created_by / updated_by con el username de la sesión actual. */
    @Bean
    public AuditorAware<String> usuarioActual() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of(USUARIO_SISTEMA);
            }
            return Optional.of(auth.getName());
        };
    }
}
