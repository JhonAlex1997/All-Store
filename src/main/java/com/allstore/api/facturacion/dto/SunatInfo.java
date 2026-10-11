package com.allstore.api.facturacion.dto;

import com.allstore.api.facturacion.entity.EstadoSunat;

/**
 * Lo que la caja necesita del envío a SUNAT para imprimir y mostrar el comprobante.
 *
 * @param hash     resumen de la firma digital; va impreso en el comprobante
 * @param codigoQr texto del código QR que exige SUNAT en la representación impresa:
 *                 RUC|TIPO|SERIE|NÚMERO|IGV|TOTAL|FECHA|TIPO DOC. CLIENTE|NÚM. DOC. CLIENTE|HASH|
 */
public record SunatInfo(
        EstadoSunat estado,
        String hash,
        String codigoQr,
        String codigoRespuesta,
        String descripcionRespuesta
) {
}
