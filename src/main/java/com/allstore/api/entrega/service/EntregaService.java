package com.allstore.api.entrega.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.common.validacion.Celular;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.config.EntregaProperties;
import com.allstore.api.entrega.dto.EntregaResponse;
import com.allstore.api.entrega.dto.EnvioCorreoResponse;
import com.allstore.api.entrega.dto.EnvioWhatsappResponse;
import com.allstore.api.entrega.entity.EnvioCorreo;
import com.allstore.api.entrega.entity.EnvioWhatsapp;
import com.allstore.api.entrega.entity.EstadoCorreo;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
import com.allstore.api.entrega.repository.EnvioWhatsappRepository;
import com.allstore.api.monitoreo.LatidoProcesos;
import com.allstore.api.monitoreo.RegistroErrores;
import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.evento.ComprobanteAceptado;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import com.allstore.api.venta.service.EntregaSolicitada;
import com.allstore.api.venta.service.VentaService;
import com.allstore.api.venta.service.VisibilidadComprobante;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entrega del comprobante al cliente (el emisor debe ponerlo a su disposición):
 * <ul>
 *   <li>Enlace público de descarga del PDF, XML y constancia, con un código imposible de
 *       adivinar: sirve para compartirlo por WhatsApp o donde sea.</li>
 *   <li>Correo con el PDF, el XML y la constancia adjuntos: lo pide un usuario, o sale solo
 *       cuando SUNAT acepta el comprobante y el cliente tiene correo registrado.</li>
 * </ul>
 * Cada correo queda registrado (a quién, cuándo, quién lo pidió) y, si el servidor de correo
 * falla, se reintenta en segundo plano con espera creciente.
 */
@Slf4j
@Service
public class EntregaService {

    /** Correos que un usuario puede mandar de un mismo comprobante en 24 horas (los automáticos no cuentan). */
    static final int MAXIMO_CORREOS_MANUALES_POR_DIA = 5;

    private static final int LOTE = 20;
    private static final String RUTA_PUBLICA = "/api/publico/comprobantes/";

    private final EnvioCorreoRepository envioRepository;
    private final ComprobanteRepository comprobanteRepository;
    private final VentaService ventaService;
    private final ImpresionService impresionService;
    private final EnvioSunatService envioSunatService;
    private final EmpresaService empresaService;
    private final ServicioCorreo servicioCorreo;
    private final CorreoComprobante correoComprobante;
    private final EntregaProperties properties;
    private final VisibilidadComprobante visibilidad;
    private final ServicioWhatsapp servicioWhatsapp;
    private final EnvioWhatsappRepository whatsappRepository;
    private final TransactionTemplate transaccionPropia;
    private final LatidoProcesos latido;
    private final RegistroErrores errores;

    public EntregaService(EnvioCorreoRepository envioRepository, ComprobanteRepository comprobanteRepository,
                          VentaService ventaService, ImpresionService impresionService, EnvioSunatService envioSunatService,
                          EmpresaService empresaService, ServicioCorreo servicioCorreo, CorreoComprobante correoComprobante,
                          EntregaProperties properties, PlatformTransactionManager transactionManager,
                          VisibilidadComprobante visibilidad, ServicioWhatsapp servicioWhatsapp,
                          EnvioWhatsappRepository whatsappRepository, LatidoProcesos latido, RegistroErrores errores) {
        this.visibilidad = visibilidad;
        this.servicioWhatsapp = servicioWhatsapp;
        this.whatsappRepository = whatsappRepository;
        this.latido = latido;
        this.errores = errores;
        this.envioRepository = envioRepository;
        this.comprobanteRepository = comprobanteRepository;
        this.ventaService = ventaService;
        this.impresionService = impresionService;
        this.envioSunatService = envioSunatService;
        this.empresaService = empresaService;
        this.servicioCorreo = servicioCorreo;
        this.correoComprobante = correoComprobante;
        this.properties = properties;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------- Consulta ----------

    /** Enlaces para compartir, WhatsApp, correo del cliente y los correos ya enviados. */
    @Transactional(readOnly = true)
    public EntregaResponse entrega(Long comprobanteId) {
        Comprobante c = obtener(comprobanteId);
        visibilidad.exigirVisible(c);
        SunatInfo sunat = envioSunatService.info(c);
        boolean tieneXml = sunat != null && sunat.hash() != null;
        boolean tieneCdr = sunat != null && (sunat.estado() == EstadoSunat.ACEPTADO || sunat.estado() == EstadoSunat.OBSERVADO);
        String pdf = enlace(c, "pdf");
        return new EntregaResponse(
                new EntregaResponse.Enlaces(pdf, pdf + "?formato=TICKET", tieneXml ? enlace(c, "xml") : null,
                        tieneCdr ? enlace(c, "cdr") : null),
                whatsapp(c, pdf),
                c.getCorreoEntrega() != null ? c.getCorreoEntrega() : c.getCliente().getEmail(),
                properties.correo().habilitado(),
                envioRepository.findByComprobanteIdOrderByIdDesc(comprobanteId).stream().map(EnvioCorreoResponse::from).toList(),
                servicioWhatsapp.configurado(),
                whatsappRepository.findByComprobanteIdOrderByIdDesc(comprobanteId).stream()
                        .map(w -> EnvioWhatsappResponse.from(w, null)).toList());
    }

    // ---------- WhatsApp ----------

    /**
     * Envía el comprobante por WhatsApp al celular indicado (o al del cliente). Con la API de Meta
     * configurada lo envía el sistema con el PDF adjunto; si no, devuelve el enlace wa.me con el
     * mensaje listo para que la caja lo envíe a mano. Mismo control que el correo: solo
     * comprobantes visibles para el usuario y hasta 5 envíos al día por comprobante.
     */
    @Transactional
    public EnvioWhatsappResponse enviarWhatsapp(Long comprobanteId, String celular) {
        Comprobante c = obtener(comprobanteId);
        visibilidad.exigirVisible(c);
        validarEntregable(c);
        String telefono = Celular.paraWhatsapp(StringUtils.hasText(celular) ? celular
                : c.getWhatsappEntrega() != null ? c.getWhatsappEntrega() : c.getCliente().getTelefono());
        String pdf = enlace(c, "pdf");
        String manual = whatsapp(c, pdf, telefono);
        if (!servicioWhatsapp.configurado()) {
            return EnvioWhatsappResponse.enlace(telefono, manual);
        }
        long hoy = whatsappRepository.countByComprobanteIdAndCreatedAtAfter(comprobanteId, ahora().minusDays(1));
        if (hoy >= MAXIMO_CORREOS_MANUALES_POR_DIA) {
            throw new BusinessException(c.getNumeroImpreso() + " ya se envió " + hoy + " veces por WhatsApp en las últimas "
                    + "24 horas; es el máximo");
        }
        EnvioWhatsapp envio = new EnvioWhatsapp();
        envio.setComprobante(c);
        envio.setTelefono(telefono);
        envio.setEstado(EnvioWhatsapp.Estado.PENDIENTE);
        envio.setIntentos(EnvioWhatsapp.MAXIMO_INTENTOS - 1); // pedido por un usuario: un solo intento, ve el resultado al instante
        enviarWhatsapp(envio);
        whatsappRepository.save(envio);
        return EnvioWhatsappResponse.from(envio, manual);
    }

    /** Un intento: ENVIADO, o de vuelta a PENDIENTE hasta agotar los intentos (entonces ERROR). */
    private void enviarWhatsapp(EnvioWhatsapp envio) {
        Comprobante c = envio.getComprobante();
        envio.setIntentos(envio.getIntentos() + 1);
        try {
            envio.setMensajeId(servicioWhatsapp.enviar(envio.getTelefono(), enlace(c, "pdf"), c.getNumeroImpreso() + ".pdf", List.of(
                    generico(c) ? "cliente" : c.getClienteNombre(),
                    c.getTipoComprobante().getDescripcion().toLowerCase() + " " + c.getNumeroImpreso(),
                    "S/ " + c.getTotal().toPlainString(),
                    negocio())));
            envio.setEstado(EnvioWhatsapp.Estado.ENVIADO);
            envio.setError(null);
        } catch (IOException e) {
            log.warn("No se pudo enviar {} por WhatsApp: {}", c.getNumeroImpreso(), e.getMessage());
            envio.setEstado(envio.getIntentos() >= EnvioWhatsapp.MAXIMO_INTENTOS ? EnvioWhatsapp.Estado.ERROR : EnvioWhatsapp.Estado.PENDIENTE);
            envio.setError(e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());
        }
    }

    private String negocio() {
        return empresaService.buscarEntidad()
                .map(e -> StringUtils.hasText(e.getNombreComercial()) ? e.getNombreComercial() : e.getRazonSocial())
                .orElse("nuestra tienda");
    }

    private static boolean generico(Comprobante c) {
        return c.getCliente().isGenerico();
    }

    // ---------- Correo ----------

    /**
     * Envía ahora el comprobante por correo (a la dirección indicada o, si no, a la del cliente).
     * Si el servidor de correo falla, queda PENDIENTE y se reintenta solo.
     */
    @Transactional
    public EnvioCorreoResponse enviarCorreo(Long comprobanteId, String email) {
        if (!properties.correo().habilitado()) {
            throw new BusinessException("El envío de comprobantes por correo no está configurado (app.entrega.correo.modo)");
        }
        Comprobante c = obtener(comprobanteId);
        visibilidad.exigirVisible(c);
        validarEntregable(c);
        long hoy = envioRepository.countByComprobanteIdAndAutomaticoFalseAndCreatedAtAfter(comprobanteId,
                OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA).minusDays(1));
        if (hoy >= MAXIMO_CORREOS_MANUALES_POR_DIA) {
            // Evita usar el correo de la tienda para enviar comprobantes en masa a cualquier dirección.
            throw new BusinessException(c.getNumeroImpreso() + " ya se envió " + hoy + " veces por correo en las últimas 24 "
                    + "horas; es el máximo. Compártelo por el enlace o por WhatsApp");
        }
        String destinatario = StringUtils.hasText(email) ? email.trim().toLowerCase() : c.getCliente().getEmail();
        if (!StringUtils.hasText(destinatario)) {
            throw new BusinessException("El cliente " + c.getClienteNombre() + " no tiene correo registrado; indica a qué correo enviarlo");
        }
        EnvioCorreo envio = new EnvioCorreo();
        envio.setComprobante(c);
        envio.setDestinatario(destinatario);
        envioRepository.save(envio);
        enviar(envio);
        return EnvioCorreoResponse.from(envio);
    }

    /**
     * Cuando SUNAT acepta un comprobante cuyo cliente tiene correo, deja el envío encolado (una
     * sola vez). Corre dentro de la transacción que guarda la respuesta de SUNAT: solo registra,
     * el correo lo envía después el proceso programado.
     */
    @EventListener
    public void alAceptarSunat(ComprobanteAceptado evento) {
        encolarEntrega(obtener(evento.comprobanteId()));
    }

    /** Nota de venta con entrega pedida al emitir: no espera a SUNAT. */
    @EventListener
    public void alSolicitarEntrega(EntregaSolicitada evento) {
        encolarEntrega(obtener(evento.comprobanteId()));
    }

    /**
     * Deja en cola, una sola vez, el correo (al elegido al emitir o, si no, al del cliente) y el
     * WhatsApp pedido al emitir. Solo registra: los envían después los procesos programados.
     */
    private void encolarEntrega(Comprobante c) {
        if (c.getEstado() != EstadoComprobante.EMITIDO) {
            return;
        }
        String email = c.getCorreoEntrega() != null ? c.getCorreoEntrega() : c.getCliente().getEmail();
        if (properties.correo().habilitado() && properties.correo().envioAutomatico() && StringUtils.hasText(email)
                && !envioRepository.existsByComprobanteIdAndAutomaticoTrue(c.getId())) {
            EnvioCorreo envio = new EnvioCorreo();
            envio.setComprobante(c);
            envio.setDestinatario(email);
            envio.setAutomatico(true);
            envioRepository.save(envio);
        }
        // Sin la API de Meta no se encola: la caja abre el enlace wa.me (ver entrega()).
        if (c.getWhatsappEntrega() != null && servicioWhatsapp.configurado()
                && !whatsappRepository.existsByComprobanteIdAndAutomaticoTrue(c.getId())) {
            EnvioWhatsapp envio = new EnvioWhatsapp();
            envio.setComprobante(c);
            envio.setTelefono(c.getWhatsappEntrega());
            envio.setEstado(EnvioWhatsapp.Estado.PENDIENTE);
            envio.setAutomatico(true);
            whatsappRepository.save(envio);
        }
    }

    /** Envía los WhatsApp pendientes cada cierto tiempo, cada uno en su propia transacción. */
    @Scheduled(fixedDelayString = "${app.entrega.correo.intervalo-ms:30000}", initialDelay = 25_000)
    public void enviarWhatsappPendientes() {
        latido.ejecutar(LatidoProcesos.WHATSAPP, this::enviarLoteWhatsapp);
    }

    private void enviarLoteWhatsapp() {
        if (!servicioWhatsapp.configurado()) {
            return;
        }
        for (Long id : whatsappRepository.idsPendientes(PageRequest.of(0, LOTE))) {
            try {
                transaccionPropia.executeWithoutResult(s -> whatsappRepository.tomarParaEnviar(id)
                        .filter(e -> e.getEstado() == EnvioWhatsapp.Estado.PENDIENTE)
                        .ifPresent(this::enviarWhatsapp));
            } catch (RuntimeException e) {
                log.error("Error enviando el WhatsApp {}", id, e);
                errores.proceso(LatidoProcesos.WHATSAPP, e);
            }
        }
    }

    /** Envía los correos pendientes cada cierto tiempo (app.entrega.correo.intervalo-ms). */
    @Scheduled(fixedDelayString = "${app.entrega.correo.intervalo-ms:30000}", initialDelay = 20_000)
    public void enviarPendientes() {
        latido.ejecutar(LatidoProcesos.CORREOS, this::enviarLoteCorreos);
    }

    private void enviarLoteCorreos() {
        if (!properties.correo().habilitado()) {
            return;
        }
        for (Long id : envioRepository.idsPorEnviar(ahora(), PageRequest.of(0, LOTE))) {
            try {
                // Cada correo en su propia transacción: un fallo no afecta a los demás.
                transaccionPropia.executeWithoutResult(s -> envioRepository.tomarParaEnviar(id)
                        .filter(e -> e.getEstado() == EstadoCorreo.PENDIENTE)
                        .ifPresent(this::enviar));
            } catch (RuntimeException e) {
                log.error("Error enviando el correo {}", id, e);
                errores.proceso(LatidoProcesos.CORREOS, e);
            }
        }
    }

    /**
     * Arma y envía el correo. Un fallo (servidor caído, dirección rechazada) no se propaga: queda
     * registrado y se reintenta con espera creciente (1, 2, 4, 8 minutos...) hasta el máximo.
     */
    private void enviar(EnvioCorreo envio) {
        envio.setIntentos(envio.getIntentos() + 1);
        Comprobante c = envio.getComprobante();
        try {
            Empresa empresa = empresaService.buscarEntidad()
                    .orElseThrow(() -> new IllegalStateException("Faltan los datos de la empresa"));
            VentaResponse venta = ventaService.obtenerDetalle(c.getId());
            List<CorreoComprobante.Adjunto> adjuntos = new ArrayList<>();
            ImpresionService.Pdf pdf = impresionService.generar(c.getId(), FormatoImpresion.A4);
            adjuntos.add(new CorreoComprobante.Adjunto(pdf.nombreArchivo(), pdf.contenido(), "application/pdf"));
            envioSunatService.archivosParaCliente(c.getId()).forEach(a -> adjuntos.add(new CorreoComprobante.Adjunto(
                    a.nombre(), a.contenido(), a.nombre().endsWith(".zip") ? "application/zip" : "application/xml")));

            MimeMessage mensaje = servicioCorreo.nuevoMensaje();
            correoComprobante.armar(mensaje, venta, empresa, envio.getDestinatario(), servicioCorreo.remitente(empresa),
                    enlace(c, "pdf"), adjuntos);
            servicioCorreo.enviar(mensaje, venta.numero() + "-" + envio.getId());

            envio.setEstado(EstadoCorreo.ENVIADO);
            envio.setFechaEnvio(ahora());
            envio.setProximoIntento(null);
            envio.setError(null);
        } catch (Exception e) {
            log.warn("No se pudo enviar {} a {}: {}", c.getNumeroImpreso(), envio.getDestinatario(), e.getMessage());
            String mensaje = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            envio.setError(mensaje.length() <= EnvioCorreo.ERROR_MAXIMO ? mensaje : mensaje.substring(0, EnvioCorreo.ERROR_MAXIMO));
            if (envio.getIntentos() >= properties.correo().maxIntentos()) {
                envio.setEstado(EstadoCorreo.ERROR);
                envio.setProximoIntento(null);
            } else {
                envio.setProximoIntento(ahora().plusMinutes(1L << (envio.getIntentos() - 1)));
            }
        }
    }

    /** No se entrega lo que no vale: anulado, nota de venta ya convertida o rechazado por SUNAT. */
    private void validarEntregable(Comprobante c) {
        switch (c.getEstado()) {
            case ANULADO -> throw new BusinessException(c.getNumeroImpreso() + " está anulado");
            case CONVERTIDO -> throw new BusinessException("La nota de venta " + c.getNumeroImpreso()
                    + " ya se convirtió en boleta o factura; envía ese comprobante");
            case REEMPLAZADO -> throw new BusinessException("SUNAT rechazó " + c.getNumeroImpreso()
                    + " y se reemitió con otro número; envía ese comprobante");
            case EMITIDO -> {
            }
        }
        SunatInfo sunat = envioSunatService.info(c);
        if (sunat != null && sunat.estado() == EstadoSunat.RECHAZADO) {
            throw new BusinessException("SUNAT rechazó " + c.getNumeroImpreso() + ": no tiene validez y no se entrega al cliente");
        }
    }

    // ---------- Descarga pública ----------

    @Transactional(readOnly = true)
    public ImpresionService.Pdf pdfPublico(UUID codigo, FormatoImpresion formato) {
        return impresionService.generar(porCodigo(codigo).getId(), formato);
    }

    @Transactional(readOnly = true)
    public EnvioSunatService.Archivo xmlPublico(UUID codigo) {
        return envioSunatService.xml(porCodigo(codigo).getId());
    }

    @Transactional(readOnly = true)
    public EnvioSunatService.Archivo cdrPublico(UUID codigo) {
        return envioSunatService.cdr(porCodigo(codigo).getId());
    }

    private Comprobante porCodigo(UUID codigo) {
        return comprobanteRepository.findByCodigoConsulta(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un comprobante con ese código"));
    }

    // ---------- Utilidades ----------

    private String enlace(Comprobante c, String archivo) {
        return properties.base() + RUTA_PUBLICA + c.getCodigoConsulta() + "/" + archivo;
    }

    /**
     * Mensaje de WhatsApp con el enlace del PDF. Si el cliente tiene un celular peruano (9 dígitos
     * que empiezan con 9) va directo a su chat; si no, WhatsApp pide elegir el contacto.
     */
    private static String whatsapp(Comprobante c, String enlacePdf) {
        if (c.getWhatsappEntrega() != null) {
            return whatsapp(c, enlacePdf, c.getWhatsappEntrega());
        }
        String telefono = c.getCliente().getTelefono() == null ? "" : c.getCliente().getTelefono().replaceAll("\\D", "");
        if (telefono.length() == 11 && telefono.startsWith("51")) {
            telefono = telefono.substring(2);
        }
        return whatsapp(c, enlacePdf, telefono.length() == 9 && telefono.startsWith("9") ? "51" + telefono : "");
    }

    /** @param destino celular con código de país, o vacío para que WhatsApp pida elegir el contacto */
    private static String whatsapp(Comprobante c, String enlacePdf, String destino) {
        String texto = "Hola, te enviamos tu " + c.getTipoComprobante().getDescripcion().toLowerCase() + " "
                + c.getNumeroImpreso() + " por S/ " + c.getTotal().toPlainString() + ". Descárgalo aquí: " + enlacePdf;
        return "https://wa.me/" + destino + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Comprobante obtener(Long id) {
        return comprobanteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
    }
}
