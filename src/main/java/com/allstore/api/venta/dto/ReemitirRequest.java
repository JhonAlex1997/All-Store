package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;

/**
 * Reemisión de un comprobante que SUNAT rechazó: la misma venta con un número nuevo. Todo es
 * opcional: sin datos se reemite igual, para cuando el rechazo vino de un error del sistema que ya
 * se corrigió. Una nota de crédito o de débito se reemite siempre con su misma serie y cliente.
 *
 * @param tipoComprobante pasar de factura a boleta o al revés (ej. el RUC del cliente no es válido)
 * @param serieId         serie del nuevo; por defecto la misma (obligatoria si cambia el tipo)
 * @param clienteId       otro cliente, o el mismo después de corregir sus datos; por defecto el mismo
 */
public record ReemitirRequest(
        TipoComprobante tipoComprobante,
        Long serieId,
        Long clienteId
) {
    public static final ReemitirRequest SIN_CAMBIOS = new ReemitirRequest(null, null, null);

    public boolean sinCambios() {
        return tipoComprobante == null && serieId == null && clienteId == null;
    }
}
