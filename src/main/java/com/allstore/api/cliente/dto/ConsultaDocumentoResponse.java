package com.allstore.api.cliente.dto;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;

/**
 * Datos de un RUC o DNI para llenar el formulario del cliente.
 *
 * @param fuente      REGISTRADO (ya es cliente: clienteId), SUNAT o RENIEC
 * @param estado      solo RUC: ACTIVO, BAJA DE OFICIO...
 * @param condicion   solo RUC: HABIDO, NO HABIDO...
 * @param advertencia si el RUC no está activo y habido: no conviene emitirle factura
 */
public record ConsultaDocumentoResponse(
        TipoDocumentoIdentidad tipoDocumento,
        String numeroDocumento,
        String nombre,
        String direccion,
        String ubigeo,
        String estado,
        String condicion,
        String fuente,
        Long clienteId,
        String advertencia
) {
}
