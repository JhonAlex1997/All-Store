package com.allstore.api.producto.service;

/** Reglas para armar los códigos que identifican a una variante. */
public final class CodigoVariante {

    /** Prefijo GS1 "2": reservado para códigos de uso interno de la tienda. */
    private static final String PREFIJO_INTERNO = "2";

    private CodigoVariante() {
    }

    /** SKU legible: MODELO-COLOR-TALLA, ej. JN001-AZU-32. */
    public static String sku(String codigoProducto, String codigoColor, String codigoTalla) {
        return (codigoProducto + "-" + codigoColor + "-" + codigoTalla).toUpperCase();
    }

    /** EAN-13 interno derivado del id de la variante, imprimible como código de barras. */
    public static String ean13(long varianteId) {
        String base = PREFIJO_INTERNO + String.format("%011d", varianteId);
        if (base.length() != 12) {
            throw new IllegalArgumentException("Id de variante demasiado grande para EAN-13: " + varianteId);
        }
        return base + digitoControlEan13(base);
    }

    static int digitoControlEan13(String doceDigitos) {
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = doceDigitos.charAt(i) - '0';
            suma += (i % 2 == 0) ? digito : digito * 3;
        }
        return (10 - suma % 10) % 10;
    }
}
