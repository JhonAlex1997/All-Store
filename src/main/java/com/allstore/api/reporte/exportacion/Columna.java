package com.allstore.api.reporte.exportacion;

import java.util.function.Function;

/**
 * Una columna de un reporte exportable: su título, cómo se formatea y de dónde sale el valor.
 *
 * @param <T> la fila del reporte
 */
public record Columna<T>(String titulo, Tipo tipo, Function<T, Object> valor) {

    public enum Tipo {
        TEXTO,
        ENTERO,
        /** Soles con 2 decimales. */
        MONEDA,
        /** Porcentaje con 1 decimal (el valor ya viene en %, ej. 12.5). */
        PORCENTAJE,
        FECHA,
        FECHA_HORA,
        SI_NO
    }

    public static <T> Columna<T> texto(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.TEXTO, valor);
    }

    public static <T> Columna<T> entero(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.ENTERO, valor);
    }

    public static <T> Columna<T> moneda(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.MONEDA, valor);
    }

    public static <T> Columna<T> porcentaje(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.PORCENTAJE, valor);
    }

    public static <T> Columna<T> fecha(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.FECHA, valor);
    }

    public static <T> Columna<T> fechaHora(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.FECHA_HORA, valor);
    }

    public static <T> Columna<T> siNo(String titulo, Function<T, Object> valor) {
        return new Columna<>(titulo, Tipo.SI_NO, valor);
    }
}
