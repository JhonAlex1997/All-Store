package com.allstore.api.contabilidad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un asiento del libro diario.
 *
 * @param origen    VENTA, NOTA_CREDITO, NOTA_DEBITO, COSTO_VENTA, COMPRA, DESTINO_COMPRA o PRODUCCION
 * @param documento el comprobante, la compra o la orden que lo origina
 */
public record AsientoResponse(int numero, LocalDate fecha, String origen, String documento, String glosa, List<Linea> lineas) {

    @Schema(name = "AsientoResponseLinea")
    public record Linea(String cuenta, String denominacion, BigDecimal debe, BigDecimal haber) {
    }

    public BigDecimal debe() {
        return lineas.stream().map(Linea::debe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal haber() {
        return lineas.stream().map(Linea::haber).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
