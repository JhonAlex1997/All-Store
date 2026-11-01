package com.allstore.api.contabilidad.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Registro de Compras Electrónico (RCE) del periodo.
 *
 * @param creditoFiscal IGV de las facturas del periodo: lo que se descuenta del IGV de las ventas
 * @param advertencias  lo que conviene revisar antes de aceptar la propuesta en el SIRE
 */
public record RegistroComprasResponse(
        String ruc,
        String razonSocial,
        String periodo,
        List<Registro> registros,
        long documentos,
        BigDecimal baseGravada,
        BigDecimal creditoFiscal,
        BigDecimal noGravado,
        BigDecimal total,
        List<String> advertencias
) {
    /**
     * @param tipoComprobante        Catálogo 10 (01 factura, 03 boleta, 12 ticket)
     * @param proveedorTipoDocumento Catálogo 06 (6 = RUC, 1 = DNI)
     * @param propuestoPorSunat      SUNAT lo trae en su propuesta (facturas); las boletas y tickets no: hay que
     *                               agregarlos en el SIRE ("Agregar" o "Importar nuevos comprobantes")
     * @param clasificacion          clasificación de los bienes y servicios adquiridos (campo 33 del RCE):
     *                               1 = mercaderías, materias primas e insumos; 5 = otros gastos y servicios
     */
    public record Registro(Long compraId, LocalDate fechaEmision, String tipoComprobante, String serie, String numero,
                           String proveedorTipoDocumento, String proveedorNumeroDocumento, String proveedorNombre,
                           BigDecimal baseGravada, BigDecimal igv, BigDecimal noGravado, BigDecimal total, String moneda,
                           boolean propuestoPorSunat, String clasificacion) {
    }
}
