package com.allstore.api.proforma.dto;

import com.allstore.api.proforma.entity.EstadoProforma;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param vencida      pendiente y con la vigencia pasada: se renueva para cobrarla
 * @param diasVigencia días que le quedan (0 = vence hoy); null si no está pendiente o ya venció
 * @param comprobante  número de la venta que la cerró (estado VENDIDA)
 * @param version      para editar: mandarla en If-Match al guardar
 */
public record ProformaResponse(
        Long id,
        String numero,
        OffsetDateTime fechaEmision,
        LocalDate vigenteHasta,
        EstadoProforma estado,
        boolean vencida,
        Integer diasVigencia,
        Long establecimientoId,
        String establecimiento,
        Cliente cliente,
        boolean porMayor,
        boolean porMayorManual,
        List<Linea> lineas,
        int prendas,
        BigDecimal opGravadas,
        BigDecimal opExoneradas,
        BigDecimal opInafectas,
        BigDecimal totalDescuento,
        BigDecimal totalIgv,
        BigDecimal total,
        String observacion,
        Long comprobanteId,
        String comprobante,
        String creadoPor,
        long version
) {
    /** @param id null si es "Clientes varios" */
    @Schema(name = "ProformaResponseCliente")
    public record Cliente(Long id, String tipoDocumento, String numeroDocumento, String nombre, String direccion,
                          String email, String telefono) {
    }

    /**
     * @param precioUnitario precio de lista cobrado (normal o por mayor)
     * @param descuento      descuento total de la línea (campaña y manual), con IGV
     * @param campana        campaña que dio el descuento, si hubo
     * @param stock          stock actual en el local (informativo: la proforma no lo reserva)
     */
    @Schema(name = "ProformaResponseLinea")
    public record Linea(int item, Long varianteId, Long productoId, String codigoModelo, String modelo, String talla,
                        String color, String sku, String descripcion, int cantidad, BigDecimal precioUnitario,
                        BigDecimal descuento, BigDecimal descuentoCampana, String campana, BigDecimal total, int stock) {
    }
}
