package com.allstore.api.facturacion.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.dto.BajaSunatInfo;
import com.allstore.api.facturacion.dto.EnvioSunatResponse;
import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.entity.ResumenSunat;
import com.allstore.api.facturacion.evento.ComprobanteAceptado;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.venta.entity.Comprobante;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.Document;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ciclo de vida del envío a SUNAT de los documentos electrónicos:
 * <ol>
 *   <li>Al emitir el documento se crea el envío PENDIENTE y se genera y firma el XML (solo CPU,
 *       sin red): así el hash y el QR están listos para imprimir el ticket al instante.</li>
 *   <li>Un proceso programado envía los pendientes. Sin comunicación, reintenta con espera
 *       creciente (1, 2, 4... hasta 60 minutos); tras el máximo de intentos queda en ERROR.</li>
 *   <li>Comprobantes: SUNAT responde en el acto y queda ACEPTADO, OBSERVADO o RECHAZADO, con su
 *       constancia (CDR). Resúmenes (bajas) y guías de remisión: SUNAT entrega un ticket, el envío
 *       queda EN_PROCESO y el mismo proceso consulta el ticket hasta tener la constancia.</li>
 * </ol>
 * La venta nunca espera a SUNAT ni falla por SUNAT.
 */
@Slf4j
@Service
public class EnvioSunatService {

    private static final Set<TipoComprobante> ELECTRONICOS =
            EnumSet.of(TipoComprobante.FACTURA, TipoComprobante.BOLETA, TipoComprobante.NOTA_CREDITO,
                    TipoComprobante.NOTA_DEBITO);
    private static final int LOTE = 20;
    /** Espera antes de la primera consulta de un ticket: SUNAT suele procesarlo en segundos. */
    private static final int SEGUNDOS_PRIMERA_CONSULTA = 20;
    /** Sin acceso configurado a la API de guías, se vuelve a revisar cada hora (sin gastar intentos). */
    private static final int MINUTOS_SIN_CONFIGURACION = 60;

    private final EnvioSunatRepository envioRepository;
    private final ResumenSunatRepository resumenRepository;
    private final GeneradorXmlUbl generador;
    private final GeneradorResumenXml generadorResumen;
    private final GeneradorGuiaXml generadorGuia;
    private final FirmadorXml firmador;
    private final ServicioEnvioSunat servicio;
    private final ServicioGuiaSunat servicioGuia;
    private final EmpresaService empresaService;
    private final SunatProperties properties;
    private final TransactionTemplate transaccionPropia;
    private final ApplicationEventPublisher eventos;

    public EnvioSunatService(EnvioSunatRepository envioRepository, ResumenSunatRepository resumenRepository,
                             GeneradorXmlUbl generador, GeneradorResumenXml generadorResumen,
                             GeneradorGuiaXml generadorGuia, FirmadorXml firmador, ServicioEnvioSunat servicio,
                             ServicioGuiaSunat servicioGuia, EmpresaService empresaService, SunatProperties properties,
                             PlatformTransactionManager transactionManager, ApplicationEventPublisher eventos) {
        this.envioRepository = envioRepository;
        this.resumenRepository = resumenRepository;
        this.generador = generador;
        this.generadorResumen = generadorResumen;
        this.generadorGuia = generadorGuia;
        this.firmador = firmador;
        this.servicio = servicio;
        this.servicioGuia = servicioGuia;
        this.empresaService = empresaService;
        this.properties = properties;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.eventos = eventos;
    }

    // ---------- Al emitir ----------

    /**
     * Registra el envío de un comprobante recién emitido (en la transacción de la venta). Si no
     * se puede generar el XML (falta la empresa o el certificado), la venta sigue igual: el
     * envío queda pendiente y se generará después.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Comprobante comprobante) {
        if (!ELECTRONICOS.contains(comprobante.getTipoComprobante())) {
            return;
        }
        EnvioSunat envio = new EnvioSunat();
        envio.setComprobante(comprobante);
        registrar(envio);
    }

    /** Registra el envío de una comunicación de baja o resumen diario recién generado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(ResumenSunat resumen) {
        EnvioSunat envio = new EnvioSunat();
        envio.setResumen(resumen);
        registrar(envio);
    }

    /** Registra el envío de una guía de remisión recién emitida (en la transacción de la guía). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(GuiaRemision guia) {
        EnvioSunat envio = new EnvioSunat();
        envio.setGuia(guia);
        registrar(envio);
    }

    private void registrar(EnvioSunat envio) {
        envio.setEntorno(properties.entorno());
        Optional<Empresa> empresa = empresaService.buscarEntidad();
        if (empresa.isPresent() && firmador.disponible()) {
            try {
                prepararXml(envio, empresa.get());
            } catch (RuntimeException e) {
                log.error("No se pudo generar el XML de {}", envio.getDocumento(), e);
                envio.setDescripcionRespuesta("No se pudo generar el XML: " + e.getMessage());
            }
        }
        envioRepository.save(envio);
    }

    // ---------- Envío ----------

    /** Revisa los pendientes y los tickets por consultar cada cierto tiempo (app.sunat.intervalo-envio-ms). */
    @Scheduled(fixedDelayString = "${app.sunat.intervalo-envio-ms:30000}", initialDelay = 15_000)
    public void enviarPendientes() {
        if (!properties.habilitado()) {
            return;
        }
        List<Long> ids = envioRepository.idsPorEnviar(ahora(), PageRequest.of(0, LOTE));
        for (Long id : ids) {
            try {
                // Cada documento en su propia transacción: un fallo no afecta a los demás.
                transaccionPropia.executeWithoutResult(s -> envioRepository.tomarParaProcesar(id)
                        .filter(e -> e.getEstado() == EstadoSunat.PENDIENTE || e.getEstado() == EstadoSunat.EN_PROCESO)
                        .ifPresent(this::procesar));
            } catch (RuntimeException e) {
                log.error("Error procesando el envío SUNAT {}", id, e);
            }
        }
    }

    /** Envío manual del comprobante desde el panel (también para reintentar uno en ERROR). */
    @Transactional
    public EnvioSunatResponse enviarAhora(Long comprobanteId, boolean regenerarXml) {
        return enviarAhora(obtener(comprobanteId), regenerarXml);
    }

    /** Envío manual de cualquier documento (comprobante o resumen) por el id de su envío. */
    @Transactional
    public EnvioSunatResponse enviarEnvioAhora(Long envioId, boolean regenerarXml) {
        return enviarAhora(obtenerEnvio(envioId), regenerarXml);
    }

    /**
     * Envía ahora sin esperar al proceso programado; si ya hay ticket, consulta su resultado.
     *
     * @param regenerarXml volver a generar y firmar el XML antes de enviar: para cuando SUNAT no
     *                     pudo leer el anterior (errores 0xxx) y ya se corrigió el sistema. Cambia
     *                     el hash, así que el comprobante debe volver a imprimirse.
     */
    private EnvioSunatResponse enviarAhora(EnvioSunat envio, boolean regenerarXml) {
        switch (envio.getEstado()) {
            case ACEPTADO, OBSERVADO -> throw new BusinessException("SUNAT ya aceptó " + envio.getDocumento());
            case RECHAZADO -> throw new BusinessException("SUNAT rechazó " + envio.getDocumento()
                    + ": no se puede reenviar con el mismo número"
                    + (envio.getComprobante() != null ? "; emite un comprobante nuevo" : ""));
            case PENDIENTE, EN_PROCESO, ERROR -> {
            }
        }
        if (envio.getTicket() != null) {
            if (regenerarXml) {
                throw new BusinessException("SUNAT ya recibió " + envio.getDocumento() + " (ticket " + envio.getTicket()
                        + "); solo se puede consultar su resultado");
            }
            envio.setEstado(EstadoSunat.EN_PROCESO);
        } else {
            envio.setEstado(EstadoSunat.PENDIENTE);
            if (regenerarXml) {
                envio.setXmlFirmado(null);
                envio.setHash(null);
            }
        }
        envio.setProximoIntento(null);
        procesar(envio);
        return EnvioSunatResponse.from(envio);
    }

    private void procesar(EnvioSunat envio) {
        if (envio.getTicket() != null) {
            consultarTicket(envio);
            return;
        }
        if (!envio.tieneXml()) {
            Optional<Empresa> empresa = empresaService.buscarEntidad();
            if (empresa.isEmpty() || !firmador.disponible()) {
                envio.setDescripcionRespuesta(empresa.isEmpty() ? "Faltan los datos de la empresa"
                        : "Falta configurar el certificado digital");
                programarReintento(envio);
                return;
            }
            prepararXml(envio, empresa.get());
        }
        if (envio.getGuia() != null && !servicioGuia.configurado()) {
            envio.setDescripcionRespuesta("Falta configurar el acceso a la API de guías de SUNAT (app.sunat.gre: "
                    + "client_id y client_secret del menú SOL). SUNAT no ofrece entorno de pruebas para la guía vigente");
            envio.setProximoIntento(ahora().plusMinutes(MINUTOS_SIN_CONFIGURACION));
            return;
        }
        envio.setIntentos(envio.getIntentos() + 1);
        envio.setFechaEnvio(ahora());
        byte[] xml = envio.getXmlFirmado().getBytes(StandardCharsets.UTF_8);
        try {
            if (envio.getComprobante() != null) {
                aplicar(envio, servicio.enviar(envio.getNombreArchivo(), xml));
            } else if (envio.getGuia() != null) {
                recibirTicket(envio, servicioGuia.enviar(envio.getNombreArchivo(), xml));
            } else {
                recibirTicket(envio, servicio.enviarResumen(envio.getNombreArchivo(), xml));
            }
        } catch (IOException e) {
            log.warn("Sin comunicación con SUNAT para {}: {}", envio.getNombreArchivo(), e.getMessage());
            envio.setDescripcionRespuesta("Sin comunicación con SUNAT: " + e.getMessage());
            programarReintento(envio);
        }
    }

    /** SUNAT admitió el resumen o la guía y entregó un ticket (o lo rechazó sin admitirlo). */
    private void recibirTicket(EnvioSunat envio, ServicioEnvioSunat.Ticket ticket) {
        if (ticket.numero() == null) {
            aplicar(envio, ticket.rechazo());
            return;
        }
        envio.setTicket(ticket.numero());
        envio.setEstado(EstadoSunat.EN_PROCESO);
        envio.setDescripcionRespuesta("SUNAT recibió el archivo; se consulta el resultado con el ticket");
        envio.setProximoIntento(ahora().plusSeconds(SEGUNDOS_PRIMERA_CONSULTA));
        log.info("SUNAT recibió {} con ticket {}", envio.getNombreArchivo(), ticket.numero());
    }

    private void consultarTicket(EnvioSunat envio) {
        envio.setIntentos(envio.getIntentos() + 1);
        try {
            ServicioEnvioSunat.EstadoTicket estado = envio.getGuia() != null
                    ? servicioGuia.consultar(envio.getNombreArchivo(), envio.getTicket())
                    : servicio.consultarTicket(envio.getNombreArchivo(), envio.getTicket());
            if (estado.enProceso()) {
                envio.setDescripcionRespuesta("SUNAT aún procesa el ticket " + envio.getTicket());
                programarReintento(envio);
                return;
            }
            aplicar(envio, estado.respuesta());
        } catch (IOException e) {
            log.warn("Sin comunicación con SUNAT para consultar {}: {}", envio.getNombreArchivo(), e.getMessage());
            envio.setDescripcionRespuesta("Sin comunicación con SUNAT: " + e.getMessage());
            programarReintento(envio);
        }
    }

    /**
     * CDR con código 0 = aceptado (observado si trae notas); CDR con código 2000-3999 =
     * rechazado. Una excepción SOAP significa que SUNAT no registró el documento: con código
     * 2000-3999 es rechazo; si no, es un error del envío y se reintenta.
     */
    void aplicar(EnvioSunat envio, ServicioEnvioSunat.Respuesta respuesta) {
        envio.setFechaRespuesta(ahora());
        envio.setCodigoRespuesta(respuesta.codigo());
        envio.setDescripcionRespuesta(recortar(respuesta.descripcion(), 1000));
        int codigo = respuesta.codigoNumerico();
        if (respuesta.cdrRecibido()) {
            envio.setCdr(respuesta.cdr());
            envio.setObservaciones(respuesta.notas().isEmpty() ? null : String.join("\n", respuesta.notas()));
            envio.setProximoIntento(null);
            if (codigo == 0 || codigo >= 4000) {
                envio.setEstado(respuesta.notas().isEmpty() ? EstadoSunat.ACEPTADO : EstadoSunat.OBSERVADO);
                if (envio.getComprobante() != null) {
                    eventos.publishEvent(new ComprobanteAceptado(envio.getComprobante().getId()));
                }
            } else {
                envio.setEstado(EstadoSunat.RECHAZADO);
            }
            log.info("SUNAT {} {}: {} {}", envio.getEstado(), envio.getNombreArchivo(), respuesta.codigo(), respuesta.descripcion());
        } else if (codigo >= 2000 && codigo < 4000) {
            envio.setEstado(EstadoSunat.RECHAZADO);
            envio.setProximoIntento(null);
            log.warn("SUNAT rechazó {}: {} {}", envio.getNombreArchivo(), respuesta.codigo(), respuesta.descripcion());
        } else {
            log.warn("SUNAT devolvió error para {}: {} {}", envio.getNombreArchivo(), respuesta.codigo(), respuesta.descripcion());
            programarReintento(envio);
        }
    }

    /** Espera creciente entre intentos (1, 2, 4, 8, 16, 32 y 60 minutos); al máximo, ERROR. */
    private void programarReintento(EnvioSunat envio) {
        if (envio.getIntentos() >= properties.maxIntentos()) {
            envio.setEstado(EstadoSunat.ERROR);
            envio.setProximoIntento(null);
            return;
        }
        long minutos = Math.min(60, 1L << Math.min(envio.getIntentos(), 6));
        envio.setProximoIntento(ahora().plusMinutes(minutos));
    }

    private void prepararXml(EnvioSunat envio, Empresa empresa) {
        Document xml;
        if (envio.getComprobante() != null) {
            xml = generador.generar(envio.getComprobante(), empresa);
            envio.setNombreArchivo(GeneradorXmlUbl.nombreArchivo(envio.getComprobante(), empresa));
        } else if (envio.getGuia() != null) {
            xml = generadorGuia.generar(envio.getGuia(), empresa);
            envio.setNombreArchivo(GeneradorGuiaXml.nombreArchivo(envio.getGuia(), empresa));
        } else {
            xml = generadorResumen.generar(envio.getResumen(), empresa);
            envio.setNombreArchivo(GeneradorResumenXml.nombreArchivo(envio.getResumen(), empresa));
        }
        FirmadorXml.XmlFirmado firmado = firmador.firmar(xml);
        envio.setXmlFirmado(firmado.xml());
        envio.setHash(firmado.hash());
        envio.setDescripcionRespuesta(null);
    }

    // ---------- Consultas ----------

    /** Para la respuesta de ventas: estado, hash y QR (null si el comprobante no va a SUNAT). */
    @Transactional(readOnly = true)
    public SunatInfo info(Comprobante comprobante) {
        return envioRepository.findByComprobanteId(comprobante.getId())
                .map(e -> new SunatInfo(e.getEstado(), e.getHash(), codigoQr(comprobante, e),
                        e.getCodigoRespuesta(), e.getDescripcionRespuesta()))
                .orElse(null);
    }

    /** Estado ante SUNAT de varios comprobantes a la vez (los que no son electrónicos no aparecen). */
    @Transactional(readOnly = true)
    public Map<Long, EstadoSunat> estados(Collection<Long> comprobanteIds) {
        if (comprobanteIds.isEmpty()) {
            return Map.of();
        }
        return envioRepository.deComprobantes(comprobanteIds).stream()
                .collect(Collectors.toMap(e -> e.getComprobante().getId(), EnvioSunat::getEstado));
    }

    /** La baja del comprobante ante SUNAT y su estado (null si no se dio de baja). */
    @Transactional(readOnly = true)
    public BajaSunatInfo infoBaja(Comprobante comprobante) {
        return resumenRepository.ultimaBaja(comprobante.getId())
                .flatMap(d -> envioRepository.findByResumenId(d.getResumen().getId()))
                .map(e -> new BajaSunatInfo(e.getResumen().getTipo().getDescripcion(), e.getResumen().getNumero(),
                        e.getId(), e.getEstado(), e.getCodigoRespuesta(), e.getDescripcionRespuesta()))
                .orElse(null);
    }

    /**
     * Para la respuesta de guías: estado y, cuando SUNAT la aceptó, el enlace de consulta de su
     * constancia (va en el QR de la representación impresa).
     */
    @Transactional(readOnly = true)
    public SunatInfo infoGuia(GuiaRemision guia) {
        return envioRepository.findByGuiaId(guia.getId())
                .map(e -> new SunatInfo(e.getEstado(), e.getHash(),
                        e.getCdr() == null ? null : CdrSunat.enlaceConsulta(e.getCdr()).orElse(null),
                        e.getCodigoRespuesta(), e.getDescripcionRespuesta()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public EnvioSunatResponse consultar(Long comprobanteId) {
        return EnvioSunatResponse.from(obtener(comprobanteId));
    }

    @Transactional(readOnly = true)
    public EnvioSunatResponse consultarEnvio(Long envioId) {
        return EnvioSunatResponse.from(obtenerEnvio(envioId));
    }

    @Transactional(readOnly = true)
    public PageResponse<EnvioSunatResponse> listar(EstadoSunat estado, Pageable pageable) {
        return PageResponse.of((estado == null ? envioRepository.findAll(pageable)
                : envioRepository.findByEstado(estado, pageable)).map(EnvioSunatResponse::from));
    }

    @Transactional(readOnly = true)
    public Archivo xml(Long comprobanteId) {
        return xml(obtener(comprobanteId));
    }

    @Transactional(readOnly = true)
    public Archivo xmlDeEnvio(Long envioId) {
        return xml(obtenerEnvio(envioId));
    }

    @Transactional(readOnly = true)
    public Archivo cdr(Long comprobanteId) {
        return cdr(obtener(comprobanteId));
    }

    @Transactional(readOnly = true)
    public Archivo cdrDeEnvio(Long envioId) {
        return cdr(obtenerEnvio(envioId));
    }

    /**
     * Archivos de SUNAT que se entregan al cliente con su comprobante: el XML firmado y, si SUNAT
     * ya respondió, su constancia (CDR). Vacío si el comprobante no es electrónico.
     */
    @Transactional(readOnly = true)
    public List<Archivo> archivosParaCliente(Long comprobanteId) {
        return envioRepository.findByComprobanteId(comprobanteId).map(envio -> {
            List<Archivo> archivos = new ArrayList<>();
            if (envio.tieneXml()) {
                archivos.add(xml(envio));
            }
            if (envio.getCdr() != null) {
                archivos.add(cdr(envio));
            }
            return archivos;
        }).orElse(List.of());
    }

    private static Archivo xml(EnvioSunat envio) {
        if (!envio.tieneXml()) {
            throw new ResourceNotFoundException("El XML de " + envio.getDocumento() + " aún no se generó");
        }
        return new Archivo(envio.getNombreArchivo() + ".xml", envio.getXmlFirmado().getBytes(StandardCharsets.UTF_8));
    }

    private static Archivo cdr(EnvioSunat envio) {
        if (envio.getCdr() == null) {
            throw new ResourceNotFoundException("SUNAT aún no devolvió la constancia (CDR) de " + envio.getDocumento());
        }
        return new Archivo("R-" + envio.getNombreArchivo() + ".zip", envio.getCdr());
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    private String codigoQr(Comprobante c, EnvioSunat envio) {
        if (envio.getHash() == null) {
            return null;
        }
        String ruc = envio.getNombreArchivo().substring(0, 11);
        return String.join("|", ruc, c.getTipoComprobante().getCodigo(), c.getSerie(), String.valueOf(c.getCorrelativo()),
                c.getTotalIgv().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                c.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                c.getFechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toLocalDate().toString(),
                c.getClienteTipoDocumento(), c.getClienteNumeroDocumento(), envio.getHash()) + "|";
    }

    private EnvioSunat obtener(Long comprobanteId) {
        return envioRepository.findByComprobanteId(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Este comprobante no se envía a SUNAT (o no existe)"));
    }

    private EnvioSunat obtenerEnvio(Long envioId) {
        return envioRepository.findById(envioId).orElseThrow(() -> new ResourceNotFoundException("Envío a SUNAT", envioId));
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
