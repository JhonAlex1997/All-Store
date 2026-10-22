package com.allstore.api.facturacion.dto;

import com.allstore.api.facturacion.entity.EstadoSunat;

/**
 * Baja de un comprobante anulado ante SUNAT.
 *
 * @param documento "Comunicación de baja" (facturas y sus notas) o "Resumen diario" (boletas y sus notas)
 * @param numero    ej. RA-20261022-1
 * @param envioId   para consultarla o reenviarla desde el panel (/api/sunat/envios/{envioId})
 * @param estado    ACEPTADO = SUNAT ya registró la anulación
 */
public record BajaSunatInfo(
        String documento,
        String numero,
        Long envioId,
        EstadoSunat estado,
        String codigoRespuesta,
        String descripcionRespuesta
) {
}
