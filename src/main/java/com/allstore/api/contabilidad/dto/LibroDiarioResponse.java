package com.allstore.api.contabilidad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Libro diario del periodo y su mayor (saldo de cada cuenta).
 *
 * @param advertencias lo que no se pudo contabilizar (ej. prendas vendidas sin costo)
 */
public record LibroDiarioResponse(String periodo, List<AsientoResponse> asientos, List<Mayor> mayor, BigDecimal totalDebe,
                                  BigDecimal totalHaber, List<String> advertencias) {

    @Schema(name = "LibroDiarioResponseMayor")
    public record Mayor(String cuenta, String denominacion, BigDecimal debe, BigDecimal haber, BigDecimal saldo) {
    }
}
