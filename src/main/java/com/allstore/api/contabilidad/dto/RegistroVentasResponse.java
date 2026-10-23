package com.allstore.api.contabilidad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Registro de Ventas e Ingresos (RVIE) de un periodo: cada comprobante tal como se informa al
 * SIRE, los totales por tipo y lo que conviene revisar antes de cerrar el mes.
 *
 * @param periodo     AAAAMM
 * @param totales     suma de todos los registros (base gravada e IGV: débito fiscal del mes)
 * @param advertencias situaciones a resolver antes de aceptar o reemplazar la propuesta de SUNAT
 */
public record RegistroVentasResponse(
        String periodo,
        String ruc,
        String razonSocial,
        List<Registro> registros,
        List<Resumen> resumenPorTipo,
        Resumen totales,
        List<String> advertencias
) {
    /**
     * Un comprobante del registro. Importes en soles, con signo negativo en las notas de crédito y
     * en 0.00 si el comprobante se dio de baja o SUNAT lo rechazó (así lo exige el Anexo 3).
     *
     * @param clienteTipoDocumento vacío en boletas menores a S/ 700 sin identificar al cliente
     * @param modifica             notas de crédito y débito: el comprobante que modifican
     */
    public record Registro(
            Long comprobanteId,
            LocalDate fechaEmision,
            String tipoComprobante,
            String serie,
            long numero,
            String clienteTipoDocumento,
            String clienteNumeroDocumento,
            String clienteNombre,
            BigDecimal baseGravada,
            BigDecimal igv,
            BigDecimal exonerado,
            BigDecimal inafecto,
            BigDecimal total,
            String moneda,
            DocumentoModificado modifica,
            Situacion situacion
    ) {
    }

    public record DocumentoModificado(LocalDate fechaEmision, String tipoComprobante, String serie, long numero) {
    }

    public enum Situacion {
        /** SUNAT lo aceptó. */
        ACEPTADO,
        /** Aún no aceptado por SUNAT (pendiente de envío, en proceso o con error): revisar antes de cerrar. */
        PENDIENTE_SUNAT,
        /** Dado de baja: se informa en 0.00. */
        ANULADO,
        /** Rechazado por SUNAT: no tiene validez y se informa en 0.00. */
        RECHAZADO_SUNAT
    }

    /** @param tipoComprobante código del Catálogo 01 (null en los totales generales) */
    public record Resumen(
            String tipoComprobante,
            String descripcion,
            int cantidad,
            BigDecimal baseGravada,
            BigDecimal igv,
            BigDecimal exonerado,
            BigDecimal inafecto,
            BigDecimal total
    ) {
    }
}
