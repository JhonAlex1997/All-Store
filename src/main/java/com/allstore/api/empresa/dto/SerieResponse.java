package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;

/**
 * @param codigoTipo     código del Catálogo 01 (o NV para nota de venta)
 * @param siguienteNumero número que recibirá el próximo comprobante, ej. B001-00000124
 */
public record SerieResponse(
        Long id,
        TipoComprobante tipoComprobante,
        String codigoTipo,
        String codigo,
        Long establecimientoId,
        String establecimiento,
        long correlativoActual,
        String siguienteNumero,
        boolean electronico,
        boolean activo
) {
    public static SerieResponse from(Serie s) {
        return new SerieResponse(
                s.getId(),
                s.getTipoComprobante(),
                s.getTipoComprobante().getCodigo(),
                s.getCodigo(),
                s.getEstablecimiento().getId(),
                s.getEstablecimiento().getNombre(),
                s.getCorrelativoActual(),
                NumeroComprobante.formatoImpreso(s.getCodigo(), s.getCorrelativoActual() + 1),
                s.getTipoComprobante().isElectronico(),
                s.isActivo()
        );
    }
}
