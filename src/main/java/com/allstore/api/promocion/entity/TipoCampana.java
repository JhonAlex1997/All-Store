package com.allstore.api.promocion.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cómo rebaja la campaña el precio normal de cada prenda (con IGV). */
public enum TipoCampana {
    /** valor % menos (ej. 20 = 20 % de descuento). */
    PORCENTAJE,
    /** valor soles menos por prenda. */
    MONTO,
    /** La prenda cuesta valor soles. */
    PRECIO_FIJO;

    /** El precio con la campaña; nunca menor a S/ 0.01 ni mayor que el normal. */
    public BigDecimal aplicar(BigDecimal normal, BigDecimal valor) {
        BigDecimal precio = switch (this) {
            case PORCENTAJE -> normal.multiply(BigDecimal.valueOf(100).subtract(valor)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case MONTO -> normal.subtract(valor);
            case PRECIO_FIJO -> valor;
        };
        BigDecimal minimo = new BigDecimal("0.01");
        return precio.max(minimo).min(normal).setScale(2, RoundingMode.HALF_UP);
    }
}
