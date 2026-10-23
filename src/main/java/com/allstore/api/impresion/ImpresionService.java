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
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.MontoEnLetras;
import com.allstore.api.guia.dto.GuiaRemisionResponse;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.service.GuiaRemisionService;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.service.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Representación impresa de comprobantes y guías de remisión: reúne los datos (documento, emisor,
 * local, SUNAT), los pone en texto legible y los entrega al generador de PDF.
 */
@Service
@RequiredArgsConstructor
public class ImpresionService {

    private final VentaService ventaService;
    private final EmpresaService empresaService;
    private final EstablecimientoService establecimientoService;
    private final SunatProperties sunatProperties;
    private final ComprobantePdf comprobantePdf;
    private final GuiaRemisionService guiaService;
    private final GuiaPdf guiaPdf;

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

    public Pdf generarGuia(Long guiaId) {
        GuiaRemisionResponse guia = guiaService.obtenerDetalle(guiaId);
        Empresa empresa = empresaService.buscarEntidad()
                .orElseThrow(() -> new BusinessException("Registra los datos de la empresa antes de imprimir guías"));
        return new Pdf(guia.numero() + ".pdf", guiaPdf.generar(datosGuia(guia, empresa)));
    }

    DatosGuia datosGuia(GuiaRemisionResponse g, Empresa empresa) {
        String titulo = titulo(TipoComprobante.GUIA_REMISION_REMITENTE);
        boolean aceptada = g.sunat() != null && g.sunat().codigoQr() != null;
        return new DatosGuia(
                titulo,
                g.numero(),
                sunatProperties.entorno() == EntornoSunat.BETA ? "PRUEBA - SIN VALIDEZ TRIBUTARIA" : null,
                emisor(empresa),
                g.fechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime(),
                g.fechaTraslado(),
                g.motivo().getCodigo() + " - " + g.motivoDescripcion(),
                g.modalidad().getCodigo() + " - " + g.modalidad().getDescripcion(),
                g.comprobante(),
                documento(g.destinatario().tipoDocumento(), g.destinatario().numeroDocumento()) + " - " + g.destinatario().nombre(),
                punto(g.partida(), g.establecimiento()),
                punto(g.llegada(), g.establecimientoDestino()),
                g.pesoBrutoKg(),
                g.numeroBultos(),
                transporte(g),
                g.detalles().stream().map(d -> new DatosGuia.Linea(d.item(), d.codigo(), d.descripcion(),
                        unidad(d.unidadMedida()), d.cantidad())).toList(),
                g.observacion(),
                aceptada ? g.sunat().codigoQr() : null,
                "Representación impresa de la " + capitalizar(titulo) + "."
                        + (aceptada ? " Consulte su validez escaneando el código QR." : " Pendiente de aceptación por SUNAT."),
                g.emitidoPor());
    }

    private static String punto(GuiaRemisionResponse.Punto p, String local) {
        return p.direccion() + " (ubigeo " + p.ubigeo() + ")"
                + (p.codigoLocal() != null ? " - " + local + ", establecimiento " + p.codigoLocal() : "");
    }

    private static List<String> transporte(GuiaRemisionResponse g) {
        List<String> lineas = new ArrayList<>();
        if (g.modalidad() == ModalidadTransporte.PUBLICO) {
            GuiaRemisionResponse.Transportista t = g.transportista();
            lineas.add("Transportista: RUC " + t.ruc() + " - " + t.razonSocial()
                    + (t.registroMtc() != null ? " (registro MTC " + t.registroMtc() + ")" : ""));
            return lineas;
        }
        if (g.vehiculoM1l()) {
            lineas.add("Traslado en vehículo de categoría M1 o L");
        }
        if (g.conductor() != null) {
            GuiaRemisionResponse.Conductor c = g.conductor();
            lineas.add("Conductor: " + c.nombres() + " " + c.apellidos() + " - " + documento(c.tipoDocumento(), c.numeroDocumento())
                    + " - Licencia " + c.licencia());
        }
        if (g.placa() != null) {
            lineas.add("Vehículo: placa " + g.placa());
        }
        return lineas;
    }

    /** "RUC 20123456786", "DNI 45678912". */
    private static String documento(String codigoSunat, String numero) {
        return TipoDocumentoIdentidad.desdeCodigoSunat(codigoSunat).getDescripcion() + " " + numero;
    }

    private static DatosImpresion.Emisor emisor(Empresa empresa) {
        return new DatosImpresion.Emisor(empresa.getRuc(), empresa.getRazonSocial(), empresa.getNombreComercial(),
                empresa.getDireccionFiscal(), empresa.getTelefono(), empresa.getEmail());
    }

    DatosImpresion datos(VentaResponse v, Empresa empresa, Establecimiento local) {
        boolean electronico = v.tipoComprobante().isElectronico();
        String titulo = titulo(v.tipoComprobante());
        return new DatosImpresion(
                titulo,
                v.numero(),
                marcaDeAgua(v, electronico),
                emisor(empresa),
                local.getNombre(),
                local.getDireccion(),
                v.fechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime(),
                v.vendedor(),
                cliente(v),
                referencia(v),
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
                v.observacion(),
                v.porMayor()
        );
    }

    /** En las notas: el comprobante que modifican y el motivo con su código de SUNAT. */
    private static DatosImpresion.Referencia referencia(VentaResponse v) {
        if (v.motivoNota() != null) {
            return new DatosImpresion.Referencia(v.comprobanteOrigen(),
                    v.motivoNota().getCodigo() + " - " + v.motivoNota().getDescripcion(), v.sustento(), true);
        }
        if (v.motivoNotaDebito() != null) {
            return new DatosImpresion.Referencia(v.comprobanteOrigen(),
                    v.motivoNotaDebito().getCodigo() + " - " + v.motivoNotaDebito().getDescripcion(), v.sustento(), false);
        }
        return null;
    }

    /** Lo anulado, lo rechazado y lo emitido en el entorno de pruebas se marca para que nadie lo tome por válido. */
    private String marcaDeAgua(VentaResponse v, boolean electronico) {
        if (v.estado() == EstadoComprobante.ANULADO) {
            return "ANULADO";
        }
        if (v.sunat() != null && v.sunat().estado() == EstadoSunat.RECHAZADO) {
            return "RECHAZADO POR SUNAT";
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

    /** Códigos del Catálogo 03 en la forma que entiende el cliente: NIU = UND, ZZ (servicio) = SERV. */
    private static String unidad(String codigoSunat) {
        return switch (codigoSunat) {
            case "NIU" -> "UND";
            case "ZZ" -> "SERV";
            default -> codigoSunat;
        };
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
