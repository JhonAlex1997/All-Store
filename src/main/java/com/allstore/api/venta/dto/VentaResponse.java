package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.ComprobantePago;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Comprobante completo, con lo necesario para imprimirlo.
 *
 * @param comprobanteOrigen número del documento del que proviene (la nota de venta convertida, o
 *                          el comprobante que modifica una nota de crédito)
 * @param convertidoEn      si es una nota de venta convertida, el número de la boleta o factura
 * @param notasCredito      si es una boleta o factura, las notas de crédito emitidas sobre ella
 * @param motivoNota        solo notas de crédito: motivo del Catálogo 09
 * @param vendedor          usuario que registró el comprobante
 * @param sunat             estado ante SUNAT, hash y QR (null en notas de venta, que no se envían)
 */
public record VentaResponse(
        Long id,
        TipoComprobante tipoComprobante,
        String codigoTipo,
        String numero,
        EstadoComprobante estado,
        OffsetDateTime fechaEmision,
        Long establecimientoId,
        String establecimiento,
        Cliente cliente,
        String moneda,
        BigDecimal opGravadas,
        BigDecimal opExoneradas,
        BigDecimal opInafectas,
        BigDecimal totalDescuento,
        BigDecimal totalIgv,
        BigDecimal total,
        BigDecimal vuelto,
        String vendedor,
        String comprobanteOrigen,
        String convertidoEn,
        List<String> notasCredito,
        MotivoNotaCredito motivoNota,
        String sustento,
        Long cajaSesionId,
        String motivoAnulacion,
        OffsetDateTime fechaAnulacion,
        String observacion,
        List<Detalle> detalles,
        List<Pago> pagos,
        SunatInfo sunat
) {
    /** Datos del cliente tal como quedaron en el comprobante. */
    public record Cliente(Long id, String tipoDocumento, String numeroDocumento, String nombre, String direccion) {
    }

    public record Detalle(int item, Long varianteId, String sku, String descripcion, String unidadMedida,
                          String tipoAfectacionIgv, int cantidad, BigDecimal precioUnitario, BigDecimal descuento,
                          BigDecimal valorVenta, BigDecimal igv, BigDecimal total) {
        static Detalle from(ComprobanteDetalle d) {
            return new Detalle(d.getItem(), d.getVariante().getId(), d.getSku(), d.getDescripcion(), d.getUnidadMedida(),
                    d.getTipoAfectacionIgv(), d.getCantidad(), d.getPrecioUnitario(), d.getDescuento(),
                    d.getValorVenta(), d.getIgv(), d.getTotal());
        }
    }

    public record Pago(MetodoPago metodo, BigDecimal monto, BigDecimal montoRecibido, BigDecimal vuelto,
                       String referencia) {
        static Pago from(ComprobantePago p) {
            return new Pago(p.getMetodo(), p.getMonto(), p.getMontoRecibido(), p.getVuelto(), p.getReferencia());
        }
    }

    public static VentaResponse from(Comprobante c, String convertidoEn, List<String> notasCredito, SunatInfo sunat) {
        return new VentaResponse(
                c.getId(),
                c.getTipoComprobante(),
                c.getTipoComprobante().getCodigo(),
                c.getNumeroImpreso(),
                c.getEstado(),
                c.getFechaEmision(),
                c.getEstablecimiento().getId(),
                c.getEstablecimiento().getNombre(),
                new Cliente(c.getCliente().getId(), c.getClienteTipoDocumento(), c.getClienteNumeroDocumento(),
                        c.getClienteNombre(), c.getClienteDireccion()),
                c.getMoneda(),
                c.getOpGravadas(),
                c.getOpExoneradas(),
                c.getOpInafectas(),
                c.getTotalDescuento(),
                c.getTotalIgv(),
                c.getTotal(),
                c.getVuelto(),
                c.getCreatedBy(),
                c.getComprobanteOrigen() == null ? null : c.getComprobanteOrigen().getNumeroImpreso(),
                convertidoEn,
                notasCredito,
                c.getMotivoNota(),
                c.getSustento(),
                c.getCajaSesion() == null ? null : c.getCajaSesion().getId(),
                c.getMotivoAnulacion(),
                c.getFechaAnulacion(),
                c.getObservacion(),
                c.getDetalles().stream().map(Detalle::from).toList(),
                c.getPagos().stream().map(Pago::from).toList(),
                sunat
        );
    }
}
