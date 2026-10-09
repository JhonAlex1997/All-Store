package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.TipoComprobante;

/** Número asignado a un comprobante: serie + correlativo. */
public record NumeroComprobante(TipoComprobante tipo, String serie, long correlativo) {

    /** Forma usada en el XML de SUNAT: B001-124. */
    public String numero() {
        return serie + "-" + correlativo;
    }

    /** Forma usada en la representación impresa: B001-00000124. */
    public String numeroImpreso() {
        return formatoImpreso(serie, correlativo);
    }

    static String formatoImpreso(String serie, long correlativo) {
        return serie + "-" + String.format("%08d", correlativo);
    }
}
