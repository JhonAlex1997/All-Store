package com.allstore.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "limaDateTimeProvider")
public class JpaAuditingConfig {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    // OffsetDateTime no es soportado por el proveedor por defecto (usa LocalDateTime), así que
    // se define uno explícito anclado a la hora de Perú.
    @Bean
    public DateTimeProvider limaDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZONA_LIMA));
    }
}
