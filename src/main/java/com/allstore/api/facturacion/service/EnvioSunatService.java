package com.allstore.api.facturacion.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.dto.EnvioSunatResponse;
import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.venta.entity.Comprobante;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ciclo de vida del envío a SUNAT:
 * <ol>
 *   <li>Al emitir el comprobante se crea el envío PENDIENTE y se genera y firma el XML (solo
 *       CPU, sin red): así el hash y el QR están listos para imprimir el ticket al instante.</li>
 *   <li>Un proceso programado envía los pendientes. Sin comunicación, reintenta con espera
 *       creciente (1, 2, 4... hasta 60 minutos); tras el máximo de intentos queda en ERROR.</li>
 *   <li>Con la respuesta de SUNAT queda ACEPTADO, OBSERVADO o RECHAZADO, con su constancia (CDR).</li>
 * </ol>
 * La venta nunca espera a SUNAT ni falla por SUNAT.
 */
@Slf4j
@Service
public class EnvioSunatService {

    private static final Set<TipoComprobante> ELECTRONICOS =
            EnumSet.of(TipoComprobante.FACTURA, TipoComprobante.BOLETA, TipoComprobante.NOTA_CREDITO);
    private static final int LOTE = 20;

    private final EnvioSunatRepository envioRepository;
    private final GeneradorXmlUbl generador;
    private final FirmadorXml firmador;
    private final ServicioEnvioSunat servicio;
    private final EmpresaService empresaService;
    private final SunatProperties properties;
    private final TransactionTemplate transaccionPropia;

    public EnvioSunatService(EnvioSunatRepository envioRepository, GeneradorXmlUbl generador, FirmadorXml firmador,
                             ServicioEnvioSunat servicio, EmpresaService empresaService, SunatProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.envioRepository = envioRepository;
        this.generador = generador;
        this.firmador = firmador;
        this.servicio = servicio;
        this.empresaService = empresaService;
        this.properties = properties;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        envio.setEntorno(properties.entorno());
        Optional<Empresa> empresa = empresaService.buscarEntidad();
        if (empresa.isPresent() && firmador.disponible()) {
            try {
                prepararXml(envio, empresa.get());
            } catch (RuntimeException e) {
                log.error("No se pudo generar el XML de {}", comprobante.getNumeroImpreso(), e);
                envio.setDescripcionRespuesta("No se pudo generar el XML: " + e.getMessage());
            }
        }
        envioRepository.save(envio);
    }

    // ---------- Envío ----------

    /** Revisa los pendientes cada cierto tiempo (app.sunat.intervalo-envio-ms). */
    @Scheduled(fixedDelayString = "${app.sunat.intervalo-envio-ms:30000}", initialDelay = 15_000)
    public void enviarPendientes() {
        if (!properties.habilitado()) {
            return;
        }
        List<Long> ids = envioRepository.idsPorEnviar(ahora(), PageRequest.of(0, LOTE));
        for (Long id : ids) {
            try {
                // Cada comprobante en su propia transacción: un fallo no afecta a los demás.
                transaccionPropia.executeWithoutResult(s -> envioRepository.tomarParaProcesar(id)
                        .filter(e -> e.getEstado() == EstadoSunat.PENDIENTE)
                        .ifPresent(this::procesar));
            } catch (RuntimeException e) {
                log.error("Error procesando el envío SUNAT {}", id, e);
            }
        }
    }

    /**
     * Envío manual desde el panel (también para reintentar uno en ERROR).
     *
     * @param regenerarXml volver a generar y firmar el XML antes de enviar: para cuando SUNAT no
     *                     pudo leer el anterior (errores 0xxx) y ya se corrigió el sistema. Cambia
     *                     el hash, así que el comprobante debe volver a imprimirse.
     */
    @Transactional
    public EnvioSunatResponse enviarAhora(Long comprobanteId, boolean regenerarXml) {
        EnvioSunat envio = obtener(comprobanteId);
        switch (envio.getEstado()) {
            case ACEPTADO, OBSERVADO -> throw new BusinessException("SUNAT ya aceptó " + envio.getComprobante().getNumeroImpreso());
            case RECHAZADO -> throw new BusinessException("SUNAT rechazó " + envio.getComprobante().getNumeroImpreso()
                    + ": no se puede reenviar con el mismo número; emite un comprobante nuevo");
            case PENDIENTE, ERROR -> {
            }
        }
        envio.setEstado(EstadoSunat.PENDIENTE);
        envio.setProximoIntento(null);
        if (regenerarXml) {
            envio.setXmlFirmado(null);
            envio.setHash(null);
        }
        procesar(envio);
        return EnvioSunatResponse.from(envio);
    }

    private void procesar(EnvioSunat envio) {
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
        envio.setIntentos(envio.getIntentos() + 1);
        envio.setFechaEnvio(ahora());
        try {
            ServicioEnvioSunat.Respuesta respuesta =
                    servicio.enviar(envio.getNombreArchivo(), envio.getXmlFirmado().getBytes(StandardCharsets.UTF_8));
            aplicar(envio, respuesta);
        } catch (IOException e) {
            log.warn("Sin comunicación con SUNAT para {}: {}", envio.getNombreArchivo(), e.getMessage());
            envio.setDescripcionRespuesta("Sin comunicación con SUNAT: " + e.getMessage());
            programarReintento(envio);
        }
    }

    /**
     * CDR con código 0 = aceptado (observado si trae notas); CDR con código 2000-3999 =
     * rechazado. Una excepción SOAP significa que SUNAT no registró el comprobante: con código
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
        Comprobante comprobante = envio.getComprobante();
        FirmadorXml.XmlFirmado firmado = firmador.firmar(generador.generar(comprobante, empresa));
        envio.setXmlFirmado(firmado.xml());
        envio.setHash(firmado.hash());
        envio.setNombreArchivo(GeneradorXmlUbl.nombreArchivo(comprobante, empresa));
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

    @Transactional(readOnly = true)
    public EnvioSunatResponse consultar(Long comprobanteId) {
        return EnvioSunatResponse.from(obtener(comprobanteId));
    }

    @Transactional(readOnly = true)
    public PageResponse<EnvioSunatResponse> listar(EstadoSunat estado, Pageable pageable) {
        return PageResponse.of((estado == null ? envioRepository.findAll(pageable)
                : envioRepository.findByEstado(estado, pageable)).map(EnvioSunatResponse::from));
    }

    @Transactional(readOnly = true)
    public Archivo xml(Long comprobanteId) {
        EnvioSunat envio = obtener(comprobanteId);
        if (!envio.tieneXml()) {
            throw new ResourceNotFoundException("El XML de este comprobante aún no se generó");
        }
        return new Archivo(envio.getNombreArchivo() + ".xml", envio.getXmlFirmado().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public Archivo cdr(Long comprobanteId) {
        EnvioSunat envio = obtener(comprobanteId);
        if (envio.getCdr() == null) {
            throw new ResourceNotFoundException("SUNAT aún no devolvió la constancia (CDR) de este comprobante");
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

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
