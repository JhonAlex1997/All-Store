package com.allstore.api.reporte.exportacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Un reporte listo para responder en JSON o exportar a Excel/CSV con las mismas columnas.
 *
 * @param nombre   para el nombre del archivo ("ventas-por-producto")
 * @param titulo   título legible ("Ventas por producto")
 * @param desde    inicio del periodo (null si el reporte no es de un periodo)
 * @param hasta    fin del periodo, inclusive
 * @param filas    los datos
 * @param columnas cómo se exporta cada fila
 */
public record Reporte<T>(String nombre, String titulo, LocalDate desde, LocalDate hasta, List<T> filas,
                         List<Columna<T>> columnas) {

    public String archivo(String extension) {
        return nombre + (desde != null ? "_" + desde + "_" + hasta : "_" + LocalDate.now()) + "." + extension;
    }
}
