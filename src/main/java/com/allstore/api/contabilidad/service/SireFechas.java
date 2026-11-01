package com.allstore.api.contabilidad.service;

import com.allstore.api.config.JpaAuditingConfig;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Plazos del SIRE según SUNAT: los registros de un periodo se generan en el SIRE desde el octavo día
 * calendario del mes siguiente (cuando SUNAT ya armó la propuesta con los comprobantes electrónicos).
 * Desde el 1 de junio de 2026 todos los obligados llevan sus registros de ventas y compras en el SIRE.
 */
final class SireFechas {

    static final int DIA_DISPONIBLE = 8;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SireFechas() {
    }

    static String disponibleDesde(YearMonth periodo) {
        LocalDate desde = periodo.plusMonths(1).atDay(DIA_DISPONIBLE);
        return LocalDate.now(JpaAuditingConfig.ZONA_LIMA).isBefore(desde)
                ? "La propuesta de SUNAT de este periodo se puede generar en el SIRE desde el " + FECHA.format(desde)
                : "La propuesta de SUNAT de este periodo ya está disponible en el SIRE (desde el " + FECHA.format(desde) + ")";
    }
}
