package com.allstore.api.empresa.dto;

/**
 * Cómo se presenta la tienda: el nombre comercial, o la razón social si no tiene.
 * {@code configurada} es false mientras no se registren los datos de la empresa.
 */
public record MarcaResponse(String nombre, boolean configurada) {

    /** Nombre que se muestra antes de registrar la empresa. */
    public static final String SIN_CONFIGURAR = "Mi tienda";
}
