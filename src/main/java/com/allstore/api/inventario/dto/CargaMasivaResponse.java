package com.allstore.api.inventario.dto;

import java.util.List;

/**
 * Resultado de una carga masiva (o de su revisión previa).
 *
 * @param aplicada        true si se guardó; false si solo se revisó o si hubo errores (no se guarda nada)
 * @param filas           filas con datos leídas del archivo
 * @param modelosNuevos   productos que se crean
 * @param variantesNuevas combinaciones talla + color que se crean
 * @param unidades        prendas que entran como inventario inicial
 * @param errores         qué corregir en cada fila (la fila es la del Excel, contando el encabezado)
 */
public record CargaMasivaResponse(
        boolean aplicada,
        int filas,
        int modelosNuevos,
        int variantesNuevas,
        int unidades,
        List<ErrorFila> errores
) {
    public record ErrorFila(int fila, String mensaje) {
    }
}
