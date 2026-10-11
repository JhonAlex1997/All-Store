package com.allstore.api.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo de IGV por línea. Los precios de la tienda incluyen IGV, así que se parte del total y
 * se separa la base imponible:
 *
 * <pre>
 *   total      = cantidad x precio - descuento          (redondeado a 2 decimales)
 *   valorVenta = total / 1.18                            (redondeado a 2 decimales)
 *   igv        = total - valorVenta                      (por diferencia, nunca por separado)
 * </pre>
 *
 * Calcular el IGV por diferencia garantiza que base + IGV = total exactamente, sin céntimos
 * perdidos por redondeo; los totales del comprobante son la suma de las líneas.
 */
public final class CalculoIgv {

    /** Tasa vigente del IGV (16% IGV + 2% IPM). Si SUNAT la cambia, se cambia solo aquí. */
    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    public static final String AFECTACION_GRAVADO = "10";

    private static final BigDecimal FACTOR = BigDecimal.ONE.add(TASA_IGV);

    private CalculoIgv() {
    }

    public record Linea(BigDecimal total, BigDecimal valorVenta, BigDecimal igv) {
    }

    /**
     * @param precioUnitario precio con IGV
     * @param descuento      descuento total de la línea, con IGV
     * @param afectacion     código del Catálogo 07: "10" gravado; otros (exonerado, inafecto) sin IGV
     */
    public static Linea calcular(int cantidad, BigDecimal precioUnitario, BigDecimal descuento, String afectacion) {
        BigDecimal total = precioUnitario.multiply(BigDecimal.valueOf(cantidad))
                .subtract(descuento)
                .setScale(2, RoundingMode.HALF_UP);
        if (!AFECTACION_GRAVADO.equals(afectacion)) {
            return new Linea(total, total, BigDecimal.ZERO.setScale(2));
        }
        BigDecimal valorVenta = total.divide(FACTOR, 2, RoundingMode.HALF_UP);
        return new Linea(total, valorVenta, total.subtract(valorVenta));
    }
}
