package com.allstore.api.empresa.dto;

/**
 * Cómo se presenta la tienda: el nombre comercial, o la razón social si no tiene.
 * {@code configurada} es false mientras no se registren los datos de la empresa.
 * {@code logoVersion} es null si no hay logo; si hay, se pide en /api/publico/marca/logo?v=logoVersion
 * (cambia al subir otro, así el navegador no muestra el anterior).
 */
public record MarcaResponse(String nombre, boolean configurada, Long logoVersion) {

    /** Nombre que se muestra antes de registrar la empresa. */
    public static final String SIN_CONFIGURAR = "Mi tienda";
}
