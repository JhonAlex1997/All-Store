package com.allstore.api.entrega.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.config.EntregaProperties;
import com.allstore.api.entrega.dto.EntregaResponse;
import com.allstore.api.entrega.dto.EnvioCorreoResponse;
import com.allstore.api.entrega.entity.EnvioCorreo;
import com.allstore.api.entrega.entity.EstadoCorreo;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
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
import com.allstore.api.venta.service.VentaService;
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
    private final TransactionTemplate transaccionPropia;

    public EntregaService(EnvioCorreoRepository envioRepository, ComprobanteRepository comprobanteRepository,
                          VentaService ventaService, ImpresionService impresionService, EnvioSunatService envioSunatService,
                          EmpresaService empresaService, ServicioCorreo servicioCorreo, CorreoComprobante correoComprobante,
                          EntregaProperties properties, PlatformTransactionManager transactionManager) {
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
        SunatInfo sunat = envioSunatService.info(c);
        boolean tieneXml = sunat != null && sunat.hash() != null;
        boolean tieneCdr = sunat != null && (sunat.estado() == EstadoSunat.ACEPTADO || sunat.estado() == EstadoSunat.OBSERVADO);
        String pdf = enlace(c, "pdf");
        return new EntregaResponse(
                new EntregaResponse.Enlaces(pdf, pdf + "?formato=TICKET", tieneXml ? enlace(c, "xml") : null,
                        tieneCdr ? enlace(c, "cdr") : null),
                whatsapp(c, pdf),
                c.getCliente().getEmail(),
                properties.correo().habilitado(),
                envioRepository.findByComprobanteIdOrderByIdDesc(comprobanteId).stream().map(EnvioCorreoResponse::from).toList());
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
        validarEntregable(c);
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
        if (!properties.correo().habilitado() || !properties.correo().envioAutomatico()
                || envioRepository.existsByComprobanteIdAndAutomaticoTrue(evento.comprobanteId())) {
            return;
        }
        Comprobante c = obtener(evento.comprobanteId());
        String email = c.getCliente().getEmail();
        if (c.getEstado() != EstadoComprobante.EMITIDO || !StringUtils.hasText(email)) {
            return;
        }
        EnvioCorreo envio = new EnvioCorreo();
        envio.setComprobante(c);
        envio.setDestinatario(email);
        envio.setAutomatico(true);
        envioRepository.save(envio);
    }

    /** Envía los correos pendientes cada cierto tiempo (app.entrega.correo.intervalo-ms). */
    @Scheduled(fixedDelayString = "${app.entrega.correo.intervalo-ms:30000}", initialDelay = 20_000)
    public void enviarPendientes() {
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
            correoComprobante.armar(mensaje, venta, empresa, envio.getDestinatario(), remitente(empresa),
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
        String texto = "Hola, te enviamos tu " + c.getTipoComprobante().getDescripcion().toLowerCase() + " "
                + c.getNumeroImpreso() + " por S/ " + c.getTotal().toPlainString() + ". Descárgalo aquí: " + enlacePdf;
        String telefono = c.getCliente().getTelefono() == null ? "" : c.getCliente().getTelefono().replaceAll("\\D", "");
        if (telefono.length() == 11 && telefono.startsWith("51")) {
            telefono = telefono.substring(2);
        }
        String destino = telefono.length() == 9 && telefono.startsWith("9") ? "51" + telefono : "";
        return "https://wa.me/" + destino + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** "De:" configurado; si no, el correo de la empresa. */
    private String remitente(Empresa empresa) {
        if (StringUtils.hasText(properties.correo().remitente())) {
            return properties.correo().remitente();
        }
        if (empresa.getEmail() != null) {
            return empresa.getEmail();
        }
        throw new IllegalStateException("Falta el remitente de los correos (CORREO_REMITENTE o el correo de la empresa)");
    }

    private Comprobante obtener(Long id) {
        return comprobanteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
    }
}
