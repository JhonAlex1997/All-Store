package com.allstore.api.impresion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Todo lo que se imprime en un comprobante, ya resuelto y en texto legible. El generador de PDF
 * solo dibuja: no consulta la base ni aplica reglas, así se puede probar sin base de datos.
 *
 * @param titulo      ej. "BOLETA DE VENTA ELECTRÓNICA"
 * @param marcaDeAgua texto en diagonal (ej. "ANULADO" o "SIN VALIDEZ TRIBUTARIA"); null = ninguna
 * @param codigoQr    texto del QR de SUNAT; null si no aplica (nota de venta) o aún no se firmó
 * @param leyenda     pie obligatorio, ej. "Representación impresa de la Boleta de Venta Electrónica..."
 */
public record DatosImpresion(
        String titulo,
        String numero,
        String marcaDeAgua,
        Emisor emisor,
        String establecimiento,
        String direccionEstablecimiento,
        OffsetDateTime fechaEmision,
        String cajero,
        Cliente cliente,
        Referencia referencia,
        List<Linea> lineas,
        BigDecimal opGravadas,
        BigDecimal opExoneradas,
        BigDecimal opInafectas,
        BigDecimal totalDescuento,
        BigDecimal totalIgv,
        BigDecimal total,
        String montoEnLetras,
        List<Pago> pagos,
        BigDecimal vuelto,
        String codigoQr,
        String hash,
        String leyenda,
        String observacion
) {
    public record Emisor(String ruc, String razonSocial, String nombreComercial, String direccionFiscal,
                         String telefono, String email) {
        /** Nombre a mostrar en grande: el comercial si existe. */
        public String nombreVisible() {
            return nombreComercial != null ? nombreComercial : razonSocial;
        }
    }

    /** @param tipoDocumento etiqueta legible ("DNI", "RUC"...); null para "Clientes varios" */
    public record Cliente(String tipoDocumento, String numeroDocumento, String nombre, String direccion) {
    }

    /** Solo notas de crédito: el comprobante que modifica y por qué. */
    public record Referencia(String documento, String motivo, String sustento) {
    }

    public record Linea(int cantidad, String unidad, String codigo, String descripcion, BigDecimal precioUnitario,
                        BigDecimal descuento, BigDecimal importe) {
    }

    public record Pago(String metodo, BigDecimal monto, String referencia) {
    }
}
