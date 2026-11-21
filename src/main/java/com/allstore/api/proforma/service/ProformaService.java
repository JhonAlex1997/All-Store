package com.allstore.api.proforma.service;

import com.allstore.api.caja.service.CajaService;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.AccesoDenegadoException;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.common.validacion.Celular;
import com.allstore.api.common.version.Versiones;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.entrega.config.EntregaProperties;
import com.allstore.api.entrega.service.ServicioCorreo;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.inventario.repository.StockRepository;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.promocion.entity.Campana;
import com.allstore.api.promocion.repository.CampanaRepository;
import com.allstore.api.proforma.dto.EnviarProformaRequest;
import com.allstore.api.proforma.dto.EnvioProformaResponse;
import com.allstore.api.proforma.dto.ProformaRequest;
import com.allstore.api.proforma.dto.ProformaResponse;
import com.allstore.api.proforma.entity.EstadoProforma;
import com.allstore.api.proforma.entity.Proforma;
import com.allstore.api.proforma.entity.ProformaDetalle;
import com.allstore.api.proforma.repository.ProformaRepository;
import com.allstore.api.seguridad.service.UsuarioEnSesion;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import com.allstore.api.venta.service.VentaService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Proformas: se cotizan con el mismo cálculo de la caja, se imprimen y se envían al cliente, y se
 * cobran en la caja respetando los precios mientras estén vigentes.
 */
@Service
@RequiredArgsConstructor
public class ProformaService {

    /** Correos que se pueden mandar de una misma proforma en 24 horas. */
    static final int MAXIMO_CORREOS_POR_DIA = 5;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String RUTA_PUBLICA = "/api/publico/proformas/";

    private final ProformaRepository proformaRepository;
    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final EstablecimientoService establecimientoService;
    private final EmpresaService empresaService;
    private final StockRepository stockRepository;
    private final CampanaRepository campanaRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final ImpresionService impresionService;
    private final ServicioCorreo servicioCorreo;
    private final EntregaProperties entregaProperties;
    private final UsuarioEnSesion usuarioEnSesion;
    private final CajaService cajaService;

    // ---------- Crear y editar ----------

    @Transactional
    public ProformaResponse crear(ProformaRequest request) {
        Proforma proforma = new Proforma();
        cotizar(proforma, request);
        proformaRepository.saveAndFlush(proforma);
        return respuesta(proforma);
    }

    /**
     * Cambia prendas, cantidades, cliente u observación. Vuelve a calcular con los precios de hoy y
     * renueva la vigencia: es una cotización nueva con el mismo número.
     */
    @Transactional
    public ProformaResponse actualizar(Long id, ProformaRequest request, Long version) {
        Proforma proforma = obtenerPendiente(id);
        Versiones.verificar(version, proforma, "esta proforma");
        cotizar(proforma, request);
        proformaRepository.flush();
        return respuesta(proforma);
    }

    /** Proforma vencida (o por vencer): mismos productos con los precios y campañas de hoy, 7 días más. */
    @Transactional
    public ProformaResponse renovar(Long id, Long version) {
        Proforma proforma = obtenerPendiente(id);
        Versiones.verificar(version, proforma, "esta proforma");
        ProformaRequest mismo = new ProformaRequest(proforma.getEstablecimiento().getId(),
                proforma.getCliente().isGenerico() ? null : proforma.getCliente().getId(), proforma.getObservacion(),
                proforma.getDetalles().stream().map(d -> new com.allstore.api.venta.dto.VentaItemRequest(
                        d.getVariante().getId(), d.getCantidad(), d.getDescuento().subtract(d.getDescuentoCampana()))).toList(),
                proforma.isPorMayorManual() ? Boolean.TRUE : null);
        cotizar(proforma, mismo);
        proformaRepository.flush();
        return respuesta(proforma);
    }

    @Transactional
    public ProformaResponse anular(Long id, Long version) {
        Proforma proforma = obtenerPendiente(id);
        Versiones.verificar(version, proforma, "esta proforma");
        proforma.setEstado(EstadoProforma.ANULADA);
        proformaRepository.flush();
        return respuesta(proforma);
    }

    /** Calcula precios y totales como la caja, y fija la vigencia desde hoy. */
    private void cotizar(Proforma proforma, ProformaRequest request) {
        Establecimiento local = establecimientoService.obtener(request.establecimientoId());
        if (!local.isActivo()) {
            throw new BusinessException("El local " + local.getNombre() + " está inactivo");
        }
        exigirLocalPermitido(local.getId());
        Cliente cliente = request.clienteId() == null ? clienteService.obtenerGenericoEntidad()
                : clienteService.obtenerEntidad(request.clienteId());
        if (!cliente.isActivo()) {
            throw new BusinessException("El cliente " + cliente.getNombre() + " está inactivo");
        }
        Comprobante calculo = ventaService.cotizar(request.items(), request.porMayor(), local.getId());
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        proforma.setEstablecimiento(local);
        proforma.asignarCliente(cliente);
        proforma.setObservacion(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);
        proforma.setFechaEmision(ahora);
        proforma.setVigenteHasta(ahora.toLocalDate().plusDays(Proforma.DIAS_VIGENCIA));
        if (proforma.getId() != null && !proforma.getDetalles().isEmpty()) {
            // Hibernate inserta antes de borrar: sin este flush, la línea 1 nueva choca con la 1 vieja
            // (ux_proforma_detalle_item).
            proforma.getDetalles().clear();
            proformaRepository.flush();
        }
        proforma.reemplazarLineas(calculo);
    }

    // ---------- Cobrar ----------

    /**
     * Cobra la proforma en la caja con sus precios cotizados. La proforma queda VENDIDA y enlazada al
     * comprobante. Un reintento con la misma Idempotency-Key devuelve la misma venta.
     */
    @Transactional
    public VentaResponse vender(Long id, VentaRequest request, String clave) {
        Proforma proforma = proformaRepository.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Proforma", id));
        exigirVisible(proforma);
        VentaResponse venta = ventaService.crearDesdeProforma(request, clave, proforma);
        if (proforma.getEstado() == EstadoProforma.PENDIENTE) {
            proforma.setEstado(EstadoProforma.VENDIDA);
            proforma.setComprobante(comprobanteRepository.getReferenceById(venta.id()));
        }
        return venta;
    }

    // ---------- Consultar ----------

    @Transactional(readOnly = true)
    public ProformaResponse obtener(Long id) {
        Proforma proforma = obtenerEntidad(id);
        exigirVisible(proforma);
        return respuesta(proforma);
    }

    /**
     * @param vigencia "VIGENTE" o "VENCIDA" (solo pendientes); null = todas
     * @param busqueda número (12 o PR01-00000012), nombre o documento del cliente
     */
    @Transactional(readOnly = true)
    public PageResponse<ProformaResponse> listar(Long establecimientoId, EstadoProforma estado, String vigencia,
                                                 String busqueda, Pageable pageable) {
        Boolean vencidas = vigencia == null ? null : switch (vigencia.toUpperCase(Locale.ROOT)) {
            case "VIGENTE" -> false;
            case "VENCIDA" -> true;
            default -> throw new BusinessException("La vigencia es VIGENTE o VENCIDA");
        };
        String creadoPor = null;
        if (!usuarioEnSesion.veTodo()) {
            // El cajero ve las proformas del local donde tiene la caja abierta, o si no, las suyas.
            Long local = cajaService.establecimientoDeMiCaja().orElse(null);
            if (local != null) {
                establecimientoId = local;
            } else {
                creadoPor = usuarioEnSesion.nombre();
            }
        }
        String texto = StringUtils.hasText(busqueda) ? busqueda.trim() : null;
        String patron = texto == null ? null : "%" + texto.toUpperCase(Locale.ROOT) + "%";
        Long numero = texto == null ? null : numeroDe(texto);
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
        return PageResponse.of(proformaRepository.buscar(establecimientoId, creadoPor, estado, vencidas, hoy, patron,
                numero, pageable).map(p -> resumen(p, hoy)));
    }

    // ---------- Imprimir y enviar ----------

    @Transactional(readOnly = true)
    public ImpresionService.Pdf pdf(Long id, FormatoImpresion formato) {
        Proforma proforma = obtenerEntidad(id);
        exigirVisible(proforma);
        return impresionService.generarProforma(respuesta(proforma), formato);
    }

    /** Para el enlace que se comparte por WhatsApp: no pide sesión, solo el código (UUID) de la proforma. */
    @Transactional(readOnly = true)
    public ImpresionService.Pdf pdfPublico(UUID codigo, FormatoImpresion formato) {
        Proforma proforma = proformaRepository.findByCodigoConsulta(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe una proforma con ese código"));
        return impresionService.generarProforma(respuesta(proforma), formato);
    }

    /**
     * Enlace de WhatsApp con el mensaje listo (al celular indicado o al del cliente) y, si se indica un
     * correo (o el cliente tiene uno y se pide), el envío por correo con el PDF adjunto.
     *
     * @param porCorreo enviar también por correo
     */
    @Transactional
    public EnvioProformaResponse enviar(Long id, EnviarProformaRequest request, boolean porCorreo) {
        Proforma proforma = obtenerEntidad(id);
        exigirVisible(proforma);
        if (proforma.getEstado() == EstadoProforma.ANULADA) {
            throw new BusinessException("La proforma " + proforma.getNumeroImpreso() + " está anulada");
        }
        String pdf = enlace(proforma);
        // Un celular indicado debe ser válido; el registrado del cliente se usa solo si lo es.
        String destino;
        if (request != null && StringUtils.hasText(request.celular())) {
            destino = Celular.paraWhatsapp(request.celular());
        } else {
            destino = celularDelCliente(proforma.getCliente().getTelefono());
        }
        String correo = null;
        if (porCorreo) {
            correo = request != null && StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase()
                    : proforma.getCliente().getEmail();
            if (!StringUtils.hasText(correo)) {
                throw new BusinessException("El cliente " + proforma.getClienteNombre()
                        + " no tiene correo registrado; indica a qué correo enviarla");
            }
            enviarCorreo(proforma, correo, pdf);
        }
        return new EnvioProformaResponse(whatsapp(proforma, pdf, destino), pdf, correo);
    }

    private void enviarCorreo(Proforma proforma, String destinatario, String enlacePdf) {
        if (!entregaProperties.correo().habilitado()) {
            throw new BusinessException("El envío por correo no está configurado (app.entrega.correo.modo); "
                    + "compártela por WhatsApp o descarga el PDF");
        }
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        if (proforma.getUltimoCorreo() == null || proforma.getUltimoCorreo().isBefore(ahora.minusDays(1))) {
            proforma.setCorreosEnviados(0);
        }
        if (proforma.getCorreosEnviados() >= MAXIMO_CORREOS_POR_DIA) {
            // Evita usar el correo de la tienda para envíos masivos a cualquier dirección.
            throw new BusinessException("La proforma " + proforma.getNumeroImpreso() + " ya se envió "
                    + MAXIMO_CORREOS_POR_DIA + " veces por correo en las últimas 24 horas; compártela por WhatsApp");
        }
        Empresa empresa = empresaService.buscarEntidad()
                .orElseThrow(() -> new BusinessException("Registra los datos de la empresa antes de enviar proformas"));
        ProformaResponse datos = respuesta(proforma);
        ImpresionService.Pdf pdf = impresionService.generarProforma(datos, FormatoImpresion.A4);
        String tienda = EmpresaService.nombreVisible(empresa);
        try {
            MimeMessage mensaje = servicioCorreo.nuevoMensaje();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            helper.setFrom(servicioCorreo.remitente(empresa));
            helper.setTo(destinatario);
            helper.setSubject("Proforma " + datos.numero() + " - " + tienda);
            helper.setText("""
                    <p>Hola %s,</p>
                    <p>Te enviamos la proforma <b>%s</b> por <b>S/ %s</b>, con precios válidos hasta el <b>%s</b>.</p>
                    <p>La encuentras adjunta en PDF o en este enlace: <a href="%s">%s</a></p>
                    <p>Gracias por tu preferencia,<br>%s</p>
                    """.formatted(HtmlUtils.htmlEscape(datos.cliente().nombre()), datos.numero(), datos.total().toPlainString(),
                    FECHA.format(datos.vigenteHasta()), enlacePdf, enlacePdf, HtmlUtils.htmlEscape(tienda)), true);
            helper.addAttachment(pdf.nombreArchivo(), new org.springframework.core.io.ByteArrayResource(pdf.contenido()),
                    "application/pdf");
            servicioCorreo.enviar(mensaje, datos.numero());
        } catch (Exception e) {
            throw new BusinessException("No se pudo enviar el correo a " + destinatario + ": "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        proforma.setCorreosEnviados(proforma.getCorreosEnviados() + 1);
        proforma.setUltimoCorreo(ahora);
    }

    // ---------- Reglas ----------

    private Proforma obtenerEntidad(Long id) {
        return proformaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proforma", id));
    }

    private Proforma obtenerPendiente(Long id) {
        Proforma proforma = obtenerEntidad(id);
        exigirVisible(proforma);
        switch (proforma.getEstado()) {
            case VENDIDA -> throw new BusinessException("La proforma " + proforma.getNumeroImpreso() + " ya se cobró"
                    + (proforma.getComprobante() != null ? " con " + proforma.getComprobante().getNumeroImpreso() : "")
                    + "; ya no se puede cambiar");
            case ANULADA -> throw new BusinessException("La proforma " + proforma.getNumeroImpreso() + " está anulada");
            case PENDIENTE -> {
            }
        }
        return proforma;
    }

    /** Como los comprobantes: el administrador ve todas; el cajero, las suyas y las de su local con caja abierta. */
    private void exigirVisible(Proforma proforma) {
        if (usuarioEnSesion.veTodo() || usuarioEnSesion.nombre().equalsIgnoreCase(proforma.getCreatedBy())) {
            return;
        }
        boolean mismoLocal = cajaService.establecimientoDeMiCaja()
                .map(local -> local.equals(proforma.getEstablecimiento().getId())).orElse(false);
        if (!mismoLocal) {
            throw new AccesoDenegadoException("Solo puedes ver tus proformas o las del local donde tienes la caja abierta");
        }
    }

    /** El cajero cotiza en el local de su caja abierta; el administrador, en cualquiera. */
    private void exigirLocalPermitido(Long establecimientoId) {
        if (usuarioEnSesion.veTodo()) {
            return;
        }
        Long local = cajaService.establecimientoDeMiCaja().orElseThrow(() ->
                new BusinessException("Abre tu caja para hacer proformas: se cotiza con los precios y campañas de tu local"));
        if (!local.equals(establecimientoId)) {
            throw new AccesoDenegadoException("Solo puedes hacer proformas del local donde tienes la caja abierta");
        }
    }

    /** "12", "PR01-12" o "PR01-00000012" → 12. */
    private static Long numeroDe(String texto) {
        String digitos = texto.toUpperCase(Locale.ROOT).replaceFirst("^PR01-?", "");
        if (!digitos.matches("\\d{1,12}")) {
            return null;
        }
        return Long.parseLong(digitos);
    }

    private String enlace(Proforma p) {
        return entregaProperties.base() + RUTA_PUBLICA + p.getCodigoConsulta() + "/pdf";
    }

    private static String celularDelCliente(String telefono) {
        if (!StringUtils.hasText(telefono)) {
            return "";
        }
        try {
            return Celular.paraWhatsapp(telefono);
        } catch (BusinessException e) {
            return "";
        }
    }

    /** @param destino celular con código de país, o vacío para que WhatsApp pida elegir el contacto */
    private static String whatsapp(Proforma p, String enlacePdf, String destino) {
        String texto = "Hola, te enviamos la proforma " + p.getNumeroImpreso() + " por S/ " + p.getTotal().toPlainString()
                + ", con precios válidos hasta el " + FECHA.format(p.getVigenteHasta()) + ". Descárgala aquí: " + enlacePdf;
        return "https://wa.me/" + destino + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---------- Respuestas ----------

    private ProformaResponse resumen(Proforma p, LocalDate hoy) {
        return armar(p, hoy, List.of());
    }

    private ProformaResponse respuesta(Proforma p) {
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
        List<Long> ids = p.getDetalles().stream().map(d -> d.getVariante().getId()).toList();
        Map<Long, Integer> stock = new HashMap<>();
        if (!ids.isEmpty()) {
            stockRepository.cantidades(ids, p.getEstablecimiento().getId())
                    .forEach(fila -> stock.put((Long) fila[0], ((Number) fila[1]).intValue()));
        }
        List<Long> campanaIds = p.getDetalles().stream().map(ProformaDetalle::getCampanaId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> campanas = campanaIds.isEmpty() ? Map.of() : campanaRepository.findAllById(campanaIds).stream()
                .collect(Collectors.toMap(Campana::getId, Campana::getNombre));
        List<ProformaResponse.Linea> lineas = p.getDetalles().stream().map(d -> {
            ProductoVariante v = d.getVariante();
            return new ProformaResponse.Linea(d.getItem(), v.getId(), v.getProducto().getId(), v.getProducto().getCodigo(),
                    v.getProducto().getNombre(), v.getTalla().getCodigo(), v.getColor().getNombre(), d.getSku(),
                    d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(), d.getDescuento(), d.getDescuentoCampana(),
                    d.getCampanaId() != null ? campanas.get(d.getCampanaId()) : null, d.getTotal(),
                    stock.getOrDefault(v.getId(), 0));
        }).toList();
        return armar(p, hoy, lineas);
    }

    private static ProformaResponse armar(Proforma p, LocalDate hoy, List<ProformaResponse.Linea> lineas) {
        boolean pendiente = p.getEstado() == EstadoProforma.PENDIENTE;
        boolean vencida = pendiente && p.estaVencida(hoy);
        Cliente c = p.getCliente();
        boolean generico = c.isGenerico();
        TipoDocumentoIdentidad tipo = TipoDocumentoIdentidad.desdeCodigoSunat(p.getClienteTipoDocumento());
        ProformaResponse.Cliente cliente = new ProformaResponse.Cliente(generico ? null : c.getId(),
                generico ? null : tipo.name(), generico ? null : p.getClienteNumeroDocumento(), p.getClienteNombre(),
                p.getClienteDireccion(), generico ? null : c.getEmail(), generico ? null : c.getTelefono());
        int prendas = p.getDetalles().stream().mapToInt(ProformaDetalle::getCantidad).sum();
        return new ProformaResponse(p.getId(), p.getNumeroImpreso(), p.getFechaEmision(), p.getVigenteHasta(), p.getEstado(),
                vencida, pendiente && !vencida ? (int) ChronoUnit.DAYS.between(hoy, p.getVigenteHasta()) : null,
                p.getEstablecimiento().getId(), p.getEstablecimiento().getNombre(), cliente, p.isPorMayor(),
                p.isPorMayorManual(), lineas, prendas, p.getOpGravadas(), p.getOpExoneradas(), p.getOpInafectas(),
                p.getTotalDescuento(), p.getTotalIgv(), p.getTotal(), p.getObservacion(),
                p.getComprobante() != null ? p.getComprobante().getId() : null,
                p.getComprobante() != null ? p.getComprobante().getNumeroImpreso() : null,
                p.getCreatedBy(), p.getVersion());
    }
}
