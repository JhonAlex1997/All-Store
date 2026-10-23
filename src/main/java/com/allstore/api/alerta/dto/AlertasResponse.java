package com.allstore.api.alerta.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Alertas abiertas: primero las críticas, y en cada nivel la más antigua primero. */
public record AlertasResponse(
        OffsetDateTime revisadas,
        long criticas,
        long advertencias,
        List<Alerta> alertas
) {
}
