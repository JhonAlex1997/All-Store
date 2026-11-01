package com.allstore.api.alerta.service;

import com.allstore.api.alerta.config.AlertaProperties;
import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Nivel;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.dto.AlertasResponse;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.entity.ResumenSunatDetalle;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.facturacion.service.FirmadorXml;
import com.allstore.api.venta.entity.Comprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lo que el administrador debe atender para que la facturación electrónica no falle en silencio:
 * comprobantes rechazados o sin enviar, plazos de SUNAT por vencer, bajas rechazadas, guías cuyo
 * traslado empieza sin estar aceptadas y el certificado digital; además, las del propio sistema
 * y de otros módulos (procesos detenidos, disco, stock bajo: ver FuenteAlertas). Se calcula al consultar (no se
 * guarda): una alerta desaparece sola cuando se resuelve lo que la causó.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertaService {

    /** Por tipo; el resto se resume en una línea ("y N más"). */
    static final int MAXIMO_POR_TIPO = 20;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<EstadoSunat> SIN_ACEPTAR = Set.of(EstadoSunat.PENDIENTE, EstadoSunat.EN_PROCESO);

    private final EnvioSunatRepository envioRepository;
    private final ResumenSunatRepository resumenRepository;
    private final FirmadorXml firmador;
    private final SunatProperties sunatProperties;
    private final AlertaProperties properties;
    private final List<FuenteAlertas> fuentes;

    public AlertasResponse alertas() {
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        LocalDate hoy = ahora.toLocalDate();
        List<Alerta> alertas = new ArrayList<>();
        configuracion(alertas, hoy);
        rechazados(alertas, ahora);
        enError(alertas, ahora, hoy);
        sinAceptar(alertas, hoy);
        bajasRechazadas(alertas);
        guiasPorSalir(alertas, hoy);
        fuentes.forEach(f -> alertas.addAll(f.alertas()));
        alertas.sort(Comparator.comparing(Alerta::nivel)
                .thenComparing(Alerta::desde, Comparator.nullsFirst(Comparator.naturalOrder())));
        long criticas = alertas.stream().filter(a -> a.nivel() == Nivel.CRITICA).count();
        return new AlertasResponse(ahora, criticas, alertas.size() - criticas, alertas);
    }

    /** Envío a SUNAT activo y certificado digital vigente. */
    private void configuracion(List<Alerta> alertas, LocalDate hoy) {
        if (!sunatProperties.habilitado()) {
            alertas.add(Alerta.general(Nivel.CRITICA, Tipo.SUNAT_DESACTIVADO, "El envío a SUNAT está desactivado "
                    + "(app.sunat.habilitado): ningún comprobante se está enviando"));
        }
        firmador.vencimiento().ifPresentOrElse(vence -> {
            LocalDate dia = vence.atZone(JpaAuditingConfig.ZONA_LIMA).toLocalDate();
            long dias = ChronoUnit.DAYS.between(hoy, dia);
            if (dias < 0) {
                alertas.add(Alerta.general(Nivel.CRITICA, Tipo.CERTIFICADO, "El certificado digital venció el "
                        + FECHA.format(dia) + ": SUNAT rechaza lo firmado con él. Renuévalo y reemplázalo en el sistema"));
            } else if (dias <= properties.diasAvisoCertificado()) {
                alertas.add(Alerta.general(Nivel.ADVERTENCIA, Tipo.CERTIFICADO, "El certificado digital vence el "
                        + FECHA.format(dia) + " (en " + dias + (dias == 1 ? " día" : " días") + "): renuévalo con tiempo; "
                        + "vencido, no se puede enviar nada a SUNAT"));
            }
        }, () -> {
            if (sunatProperties.habilitado()) {
                alertas.add(Alerta.general(Nivel.CRITICA, Tipo.CERTIFICADO, "Falta el certificado digital: los "
                        + "comprobantes no se firman ni se envían a SUNAT"));
            }
        });
    }

    /** Rechazados que siguen vigentes en el sistema: la venta no tiene un comprobante válido. */
    private void rechazados(List<Alerta> alertas, OffsetDateTime ahora) {
        agregar(alertas, Tipo.COMPROBANTE_RECHAZADO, Nivel.CRITICA,
                envioRepository.comprobantesVigentes(Set.of(EstadoSunat.RECHAZADO), ahora.plusDays(1), limite()),
                e -> alertas.add(deComprobante(Nivel.CRITICA, Tipo.COMPROBANTE_RECHAZADO, e, "SUNAT rechazó "
                        + e.getDocumento() + respuesta(e) + ". No tiene validez: reemítelo con otro número o anúlalo")));
    }

    /** Lo que agotó sus intentos de envío: no se reintenta solo. */
    private void enError(List<Alerta> alertas, OffsetDateTime ahora, LocalDate hoy) {
        Consumer<EnvioSunat> alerta = e -> {
            String plazo = e.getComprobante() != null ? " SUNAT lo recibe hasta el " + FECHA.format(limiteDeEnvio(e.getComprobante())) + "." : "";
            alertas.add(deEnvio(Nivel.CRITICA, Tipo.ENVIO_EN_ERROR, e, "No se pudo enviar " + e.getDocumento()
                    + " tras " + e.getIntentos() + " intentos" + respuesta(e) + ". Revísalo y reenvíalo desde el panel de SUNAT." + plazo));
        };
        Set<EstadoSunat> error = Set.of(EstadoSunat.ERROR);
        agregar(alertas, Tipo.ENVIO_EN_ERROR, Nivel.CRITICA, envioRepository.comprobantesVigentes(error, ahora.plusDays(1), limite()), alerta);
        agregar(alertas, Tipo.ENVIO_EN_ERROR, Nivel.CRITICA, envioRepository.resumenes(EstadoSunat.ERROR, limite()), alerta);
        agregar(alertas, Tipo.ENVIO_EN_ERROR, Nivel.CRITICA, envioRepository.guiasPorTrasladar(error, hoy.plusYears(10), limite()), alerta);
    }

    /**
     * Emitidos antes de hoy que SUNAT aún no acepta (sin comunicación, o falta configuración):
     * advertencia mientras haya plazo; crítica cuando venció.
     */
    private void sinAceptar(List<Alerta> alertas, LocalDate hoy) {
        OffsetDateTime inicioDeHoy = hoy.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        agregar(alertas, Tipo.PLAZO_DE_ENVIO, Nivel.ADVERTENCIA,
                envioRepository.comprobantesVigentes(SIN_ACEPTAR, inicioDeHoy, limite()), e -> {
                    LocalDate limite = limiteDeEnvio(e.getComprobante());
                    boolean vencido = hoy.isAfter(limite);
                    alertas.add(deComprobante(vencido ? Nivel.CRITICA : Nivel.ADVERTENCIA, Tipo.PLAZO_DE_ENVIO, e,
                            e.getDocumento() + " aún no está aceptado por SUNAT" + (vencido
                                    ? ": venció el plazo de envío (hasta el " + FECHA.format(limite) + ")"
                                    : "; SUNAT lo recibe hasta el " + FECHA.format(limite))
                                    + (e.getDescripcionRespuesta() != null ? ". Último intento: " + e.getDescripcionRespuesta() : "")));
                });
    }

    /**
     * Bajas que SUNAT rechazó y no se reintentaron: lo anulado en el sistema sigue vigente para
     * SUNAT. Deja de alertar cuando se reintenta (la baja más reciente de sus comprobantes es otra).
     */
    private void bajasRechazadas(List<Alerta> alertas) {
        agregar(alertas, Tipo.BAJA_RECHAZADA, Nivel.CRITICA, envioRepository.resumenes(EstadoSunat.RECHAZADO, limite()), e -> {
            List<String> pendientes = e.getResumen().getDetalles().stream()
                    .map(ResumenSunatDetalle::getComprobante)
                    .filter(c -> resumenRepository.ultimaBaja(c.getId())
                            .map(d -> d.getResumen().getId().equals(e.getResumen().getId())).orElse(false))
                    .map(Comprobante::getNumeroImpreso)
                    .toList();
            if (!pendientes.isEmpty()) {
                alertas.add(deEnvio(Nivel.CRITICA, Tipo.BAJA_RECHAZADA, e, "SUNAT rechazó " + e.getDocumento() + respuesta(e)
                        + ": " + String.join(", ", pendientes) + (pendientes.size() == 1 ? " sigue vigente" : " siguen vigentes")
                        + " para SUNAT aunque se anularon. Reintenta la baja desde el panel de SUNAT"));
            }
        });
    }

    /** Guías no aceptadas cuyo traslado empieza hoy o ya empezó: la mercadería no debe viajar sin guía válida. */
    private void guiasPorSalir(List<Alerta> alertas, LocalDate hoy) {
        agregar(alertas, Tipo.GUIA_SIN_ACEPTAR, Nivel.ADVERTENCIA, envioRepository.guiasPorTrasladar(SIN_ACEPTAR, hoy, limite()), e -> {
            LocalDate traslado = e.getGuia().getFechaTraslado();
            boolean empezo = traslado.isBefore(hoy);
            alertas.add(deEnvio(empezo ? Nivel.CRITICA : Nivel.ADVERTENCIA, Tipo.GUIA_SIN_ACEPTAR, e, e.getDocumento()
                    + " aún no está aceptada por SUNAT y su traslado " + (empezo ? "empezó el " + FECHA.format(traslado) : "empieza hoy")
                    + ": la mercadería no debe viajar sin una guía aceptada"
                    + (e.getDescripcionRespuesta() != null ? ". " + e.getDescripcionRespuesta() : "")));
        });
    }

    // ---------- Ayudas ----------

    /** Las alertas de la página y, si hay más, una línea que las resume. */
    private static void agregar(List<Alerta> alertas, Tipo tipo, Nivel nivel, Page<EnvioSunat> pagina, Consumer<EnvioSunat> alerta) {
        pagina.forEach(alerta);
        long resto = pagina.getTotalElements() - pagina.getNumberOfElements();
        if (resto > 0) {
            alertas.add(Alerta.general(nivel, tipo, "Y " + resto + " más del mismo tipo: revísalos en el panel de SUNAT"));
        }
    }

    /** Día hasta el que SUNAT recibe el comprobante (app.alertas.dias-plazo-envio desde su emisión). */
    private LocalDate limiteDeEnvio(Comprobante comprobante) {
        return comprobante.getFechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toLocalDate()
                .plusDays(properties.diasPlazoEnvio());
    }

    private static Alerta deComprobante(Nivel nivel, Tipo tipo, EnvioSunat e, String mensaje) {
        Comprobante c = e.getComprobante();
        return new Alerta(nivel, tipo, c.getNumeroImpreso(), mensaje, c.getFechaEmision(), c.getId(), e.getId());
    }

    private static Alerta deEnvio(Nivel nivel, Tipo tipo, EnvioSunat e, String mensaje) {
        if (e.getComprobante() != null) {
            return deComprobante(nivel, tipo, e, mensaje);
        }
        OffsetDateTime desde = e.getGuia() != null ? e.getGuia().getFechaEmision() : e.getCreatedAt();
        return new Alerta(nivel, tipo, e.getNumero(), mensaje, desde, null, e.getId());
    }

    private static String respuesta(EnvioSunat e) {
        if (e.getCodigoRespuesta() == null && e.getDescripcionRespuesta() == null) {
            return "";
        }
        return " (" + (e.getCodigoRespuesta() != null ? e.getCodigoRespuesta() + " " : "")
                + (e.getDescripcionRespuesta() != null ? e.getDescripcionRespuesta() : "").trim() + ")";
    }

    private static PageRequest limite() {
        return PageRequest.of(0, MAXIMO_POR_TIPO);
    }
}
