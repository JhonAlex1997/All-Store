package com.allstore.api.impresion;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.service.MontoEnLetras;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.service.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Representación impresa de un comprobante: reúne los datos (venta, emisor, local, SUNAT), los
 * pone en texto legible y los entrega al generador de PDF.
 */
@Service
@RequiredArgsConstructor
public class ImpresionService {

    private final VentaService ventaService;
    private final EmpresaService empresaService;
    private final EstablecimientoService establecimientoService;
    private final SunatProperties sunatProperties;
    private final ComprobantePdf comprobantePdf;

    public record Pdf(String nombreArchivo, byte[] contenido) {
    }

    public Pdf generar(Long comprobanteId, FormatoImpresion formato) {
        VentaResponse venta = ventaService.obtenerDetalle(comprobanteId);
        Empresa empresa = empresaService.buscarEntidad()
                .orElseThrow(() -> new BusinessException("Registra los datos de la empresa antes de imprimir comprobantes"));
        Establecimiento local = establecimientoService.obtener(venta.establecimientoId());
        byte[] pdf = comprobantePdf.generar(datos(venta, empresa, local), formato);
        return new Pdf(venta.numero() + (formato == FormatoImpresion.TICKET ? "-ticket" : "") + ".pdf", pdf);
    }

    DatosImpresion datos(VentaResponse v, Empresa empresa, Establecimiento local) {
        boolean electronico = v.tipoComprobante().isElectronico();
        String titulo = titulo(v.tipoComprobante());
        return new DatosImpresion(
                titulo,
                v.numero(),
                marcaDeAgua(v, electronico),
                new DatosImpresion.Emisor(empresa.getRuc(), empresa.getRazonSocial(), empresa.getNombreComercial(),
                        empresa.getDireccionFiscal(), empresa.getTelefono(), empresa.getEmail()),
                local.getNombre(),
                local.getDireccion(),
                v.fechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime(),
                v.vendedor(),
                cliente(v),
                v.tipoComprobante() == TipoComprobante.NOTA_CREDITO
                        ? new DatosImpresion.Referencia(v.comprobanteOrigen(),
                        v.motivoNota().getCodigo() + " - " + v.motivoNota().getDescripcion(), v.sustento())
                        : null,
                v.detalles().stream().map(d -> new DatosImpresion.Linea(d.cantidad(), unidad(d.unidadMedida()), d.sku(),
                        d.descripcion(), d.precioUnitario(), d.descuento(), d.total())).toList(),
                v.opGravadas(),
                v.opExoneradas(),
                v.opInafectas(),
                v.totalDescuento(),
                v.totalIgv(),
                v.total(),
                MontoEnLetras.convertir(v.total()),
                v.pagos().stream().map(p -> new DatosImpresion.Pago(metodo(p.metodo()), p.monto(), p.referencia())).toList(),
                v.vuelto(),
                v.sunat() != null ? v.sunat().codigoQr() : null,
                v.sunat() != null ? v.sunat().hash() : null,
                leyenda(v, titulo, electronico),
                v.observacion()
        );
    }

    /** Lo anulado y lo emitido en el entorno de pruebas se marca para que nadie lo tome por válido. */
    private String marcaDeAgua(VentaResponse v, boolean electronico) {
        if (v.estado() == EstadoComprobante.ANULADO) {
            return "ANULADO";
        }
        if (electronico && sunatProperties.entorno() == EntornoSunat.BETA) {
            return "PRUEBA - SIN VALIDEZ TRIBUTARIA";
        }
        return null;
    }

    private static String leyenda(VentaResponse v, String titulo, boolean electronico) {
        if (!electronico) {
            return "Documento interno. No es un comprobante de pago: solicite su boleta o factura.";
        }
        String base = "Representación impresa de la " + capitalizar(titulo) + ". Consulte su validez en www.sunat.gob.pe";
        return v.sunat() != null && v.sunat().hash() == null ? base + " (pendiente de firma digital)." : base + ".";
    }

    /** El cliente tal como quedó en el comprobante; "Clientes varios" va sin documento. */
    private static DatosImpresion.Cliente cliente(VentaResponse v) {
        VentaResponse.Cliente c = v.cliente();
        TipoDocumentoIdentidad tipo = TipoDocumentoIdentidad.desdeCodigoSunat(c.tipoDocumento());
        if (tipo == TipoDocumentoIdentidad.SIN_DOCUMENTO) {
            return new DatosImpresion.Cliente(null, null, c.nombre(), null);
        }
        return new DatosImpresion.Cliente(tipo.getDescripcion(), c.numeroDocumento(), c.nombre(), c.direccion());
    }

    static String titulo(TipoComprobante tipo) {
        return switch (tipo) {
            case FACTURA -> "FACTURA ELECTRÓNICA";
            case BOLETA -> "BOLETA DE VENTA ELECTRÓNICA";
            case NOTA_CREDITO -> "NOTA DE CRÉDITO ELECTRÓNICA";
            case NOTA_DEBITO -> "NOTA DE DÉBITO ELECTRÓNICA";
            case GUIA_REMISION_REMITENTE -> "GUÍA DE REMISIÓN REMITENTE ELECTRÓNICA";
            case NOTA_VENTA -> "NOTA DE VENTA";
        };
    }

    private static String metodo(MetodoPago metodo) {
        return switch (metodo) {
            case EFECTIVO -> "Efectivo";
            case TARJETA_DEBITO -> "Tarjeta de débito";
            case TARJETA_CREDITO -> "Tarjeta de crédito";
            case YAPE -> "Yape";
            case PLIN -> "Plin";
            case TRANSFERENCIA -> "Transferencia";
        };
    }

    /** NIU (Catálogo 03) se imprime como UND, que es lo que entiende el cliente. */
    private static String unidad(String codigoSunat) {
        return "NIU".equals(codigoSunat) ? "UND" : codigoSunat;
    }

    /** "BOLETA DE VENTA ELECTRÓNICA" → "Boleta de Venta Electrónica". */
    private static String capitalizar(String titulo) {
        List<String> menores = List.of("de", "la", "del");
        StringBuilder sb = new StringBuilder();
        for (String palabra : titulo.toLowerCase().split(" ")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(menores.contains(palabra) ? palabra : Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1));
        }
        return sb.toString();
    }
}
