package com.allstore.api.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importe en letras para la leyenda 1000 del comprobante, ej. 179.80 →
 * "CIENTO SETENTA Y NUEVE CON 80/100 SOLES". Soporta hasta 999 999 999.99.
 */
public final class MontoEnLetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {
    }

    public static String convertir(BigDecimal monto) {
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (entero > 999_999_999L) {
            throw new IllegalArgumentException("Monto demasiado grande para expresar en letras: " + monto);
        }
        String letras = entero == 0 ? "CERO" : enteroEnLetras(entero);
        return letras + " CON " + String.format("%02d", centimos) + "/100 SOLES";
    }

    private static String enteroEnLetras(long n) {
        StringBuilder sb = new StringBuilder();
        long millones = n / 1_000_000;
        long miles = (n / 1_000) % 1_000;
        long resto = n % 1_000;
        if (millones > 0) {
            sb.append(millones == 1 ? "UN MILLON" : apocopar(centenas((int) millones)) + " MILLONES");
        }
        if (miles > 0) {
            espacio(sb).append(miles == 1 ? "MIL" : apocopar(centenas((int) miles)) + " MIL");
        }
        if (resto > 0) {
            espacio(sb).append(centenas((int) resto));
        }
        return sb.toString();
    }

    /** 1 a 999. */
    private static String centenas(int n) {
        if (n == 100) {
            return "CIEN";
        }
        int c = n / 100;
        int resto = n % 100;
        String decenas = decenas(resto);
        if (c == 0) {
            return decenas;
        }
        return decenas.isEmpty() ? CENTENAS[c] : CENTENAS[c] + " " + decenas;
    }

    /** 0 a 99. */
    private static String decenas(int n) {
        if (n < 30) {
            return UNIDADES[n];
        }
        int d = n / 10;
        int u = n % 10;
        return u == 0 ? DECENAS[d] : DECENAS[d] + " Y " + UNIDADES[u];
    }

    /** Delante de MIL o MILLONES, "UNO" se dice "UN" (veintiún mil, treinta y un millones). */
    private static String apocopar(String letras) {
        if (letras.endsWith("UNO")) {
            return letras.substring(0, letras.length() - 1);
        }
        return letras;
    }

    private static StringBuilder espacio(StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        return sb;
    }
}
