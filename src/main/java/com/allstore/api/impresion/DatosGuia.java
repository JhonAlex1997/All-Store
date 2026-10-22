package com.allstore.api.impresion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Todo lo que se imprime en una guía de remisión, ya resuelto y en texto legible (como
 * {@link DatosImpresion} para los comprobantes).
 *
 * @param documentoRelacionado ej. "Factura F001-00000004" (solo ventas)
 * @param transporte           líneas con el transportista o el conductor y el vehículo
 * @param enlaceQr             enlace de consulta de la constancia de SUNAT; null si aún no la aceptó
 */
public record DatosGuia(
        String titulo,
        String numero,
        String marcaDeAgua,
        DatosImpresion.Emisor emisor,
        OffsetDateTime fechaEmision,
        LocalDate fechaTraslado,
        String motivo,
        String modalidad,
        String documentoRelacionado,
        String destinatario,
        String partida,
        String llegada,
        BigDecimal pesoBrutoKg,
        Integer numeroBultos,
        List<String> transporte,
        List<Linea> lineas,
        String observacion,
        String enlaceQr,
        String leyenda,
        String emitidoPor
) {
    public record Linea(int item, String codigo, String descripcion, String unidad, int cantidad) {
    }
}
