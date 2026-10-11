package com.allstore.api.caja.dto;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.entity.EstadoCaja;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Fila del historial de cajas (para el administrador). Los montos son los guardados al cerrar. */
public record CajaSesionResumenResponse(
        Long id,
        String establecimiento,
        String usuario,
        EstadoCaja estado,
        OffsetDateTime fechaApertura,
        OffsetDateTime fechaCierre,
        BigDecimal montoApertura,
        BigDecimal totalVentas,
        BigDecimal efectivoEsperado,
        BigDecimal efectivoContado,
        BigDecimal diferencia
) {
    public static CajaSesionResumenResponse from(CajaSesion s) {
        return new CajaSesionResumenResponse(s.getId(), s.getEstablecimiento().getNombre(), s.getUsuario(), s.getEstado(),
                s.getFechaApertura(), s.getFechaCierre(), s.getMontoApertura(), s.getTotalVentas(),
                s.getEfectivoEsperado(), s.getEfectivoContado(), s.getDiferencia());
    }
}
