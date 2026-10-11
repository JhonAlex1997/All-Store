package com.allstore.api.caja.dto;

import com.allstore.api.caja.entity.CajaMovimiento;
import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.entity.EstadoCaja;
import com.allstore.api.caja.entity.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Caja con su cuadre. Mientras está abierta, el resumen se calcula al momento; al cerrar se
 * agregan lo contado y la diferencia (contado - esperado: positivo sobra, negativo falta).
 */
public record CajaResponse(
        Long id,
        Long establecimientoId,
        String establecimiento,
        String usuario,
        EstadoCaja estado,
        OffsetDateTime fechaApertura,
        OffsetDateTime fechaCierre,
        ResumenCaja resumen,
        BigDecimal efectivoContado,
        BigDecimal diferencia,
        String observacionCierre,
        List<Movimiento> movimientos
) {
    public record Movimiento(Long id, OffsetDateTime fecha, TipoMovimientoCaja tipo, BigDecimal monto, String concepto,
                             String usuario) {
        public static Movimiento from(CajaMovimiento m) {
            return new Movimiento(m.getId(), m.getCreatedAt(), m.getTipo(), m.getMonto(), m.getConcepto(), m.getCreatedBy());
        }
    }

    public static CajaResponse from(CajaSesion s, ResumenCaja resumen, List<CajaMovimiento> movimientos) {
        return new CajaResponse(s.getId(), s.getEstablecimiento().getId(), s.getEstablecimiento().getNombre(),
                s.getUsuario(), s.getEstado(), s.getFechaApertura(), s.getFechaCierre(), resumen,
                s.getEfectivoContado(), s.getDiferencia(), s.getObservacionCierre(),
                movimientos.stream().map(Movimiento::from).toList());
    }
}
