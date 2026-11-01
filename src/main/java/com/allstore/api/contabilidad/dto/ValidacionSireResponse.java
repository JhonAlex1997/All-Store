package com.allstore.api.contabilidad.dto;

import java.util.List;

/**
 * Revisión del archivo de reemplazo del SIRE con las reglas de la estructura oficial de SUNAT, antes
 * de llevarlo al validador PVSIRE.
 *
 * @param valido         true si no se encontró ninguna inconsistencia
 * @param inconsistencias fila 0 = el nombre del archivo
 */
public record ValidacionSireResponse(String registro, String archivo, int filas, boolean valido,
                                     List<Inconsistencia> inconsistencias) {

    /**
     * @param fila  número de línea del archivo (0 = nombre del archivo)
     * @param campo número de campo según la estructura oficial (0 = toda la línea)
     */
    public record Inconsistencia(int fila, int campo, String nombreCampo, String valor, String mensaje) {
    }
}
