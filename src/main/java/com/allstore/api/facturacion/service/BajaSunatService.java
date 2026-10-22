package com.allstore.api.facturacion.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.dto.BajaSunatInfo;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.entity.ResumenSunat;
import com.allstore.api.facturacion.entity.ResumenSunatDetalle;
import com.allstore.api.facturacion.entity.TipoResumen;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.venta.entity.Comprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Baja de comprobantes electrónicos ante SUNAT:
 * <ul>
 *   <li>Factura y sus notas (serie F): comunicación de baja (RA).</li>
 *   <li>Boleta y sus notas (serie B): resumen diario que la informa como anulada (RC, estado 3).</li>
 * </ul>
 * Procede si SUNAT ya aceptó el comprobante y dentro de los 7 días calendario siguientes a su
 * constancia de aceptación. Pasado el plazo, el comprobante se corrige con una nota de crédito.
 *
 * <p>Cada baja va en su propio documento (RA-20261022-1, RA-20261022-2...): SUNAT exige que los
 * comprobantes de un mismo documento sean del mismo día, y así un rechazo no arrastra a otros.
 */
@Service
@RequiredArgsConstructor
public class BajaSunatService {

    /** Días calendario, contados desde el día siguiente a la constancia de aceptación. */
    public static final int PLAZO_DIAS = 7;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ResumenSunatRepository resumenRepository;
    private final EnvioSunatRepository envioRepository;
    private final EnvioSunatService envioSunatService;

    /** Verifica que el comprobante se puede dar de baja; si no, explica qué hacer en su lugar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validar(Comprobante c) {
        String numero = c.getNumeroImpreso();
        EnvioSunat envio = envioRepository.findByComprobanteId(c.getId())
                .orElseThrow(() -> new BusinessException(numero + " no tiene envío a SUNAT"));
        switch (envio.getEstado()) {
            case ACEPTADO, OBSERVADO -> {
            }
            case PENDIENTE, EN_PROCESO -> throw new BusinessException("SUNAT aún no recibe " + numero
                    + ": la baja procede cuando lo haya aceptado. Puedes enviarlo ahora desde el panel de SUNAT");
            case RECHAZADO -> throw new BusinessException("SUNAT rechazó " + numero
                    + ": no tiene validez tributaria y no se da de baja");
            case ERROR -> throw new BusinessException("No se pudo enviar " + numero
                    + " a SUNAT; reenvíalo desde el panel de SUNAT antes de anularlo");
        }
        OffsetDateTime aceptado = envio.getFechaRespuesta() != null ? envio.getFechaRespuesta() : c.getFechaEmision();
        LocalDate limite = aceptado.atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toLocalDate().plusDays(PLAZO_DIAS);
        if (LocalDate.now(JpaAuditingConfig.ZONA_LIMA).isAfter(limite)) {
            boolean venta = c.getTipoComprobante() == TipoComprobante.FACTURA || c.getTipoComprobante() == TipoComprobante.BOLETA;
            throw new BusinessException("Venció el plazo de SUNAT para dar de baja " + numero + " (" + PLAZO_DIAS
                    + " días, hasta el " + FECHA.format(limite) + ")" + (venta ? ": corrígelo con una nota de crédito" : ""));
        }
    }

    /**
     * Vuelve a dar de baja un comprobante anulado cuya baja rechazó SUNAT (por ejemplo, tras
     * corregir el sistema), con un documento nuevo: SUNAT no admite reenviar el mismo número.
     */
    @Transactional
    public BajaSunatInfo reintentar(Long comprobanteId) {
        ResumenSunatDetalle anterior = resumenRepository.ultimaBaja(comprobanteId)
                .orElseThrow(() -> new BusinessException("Este comprobante no tiene una baja ante SUNAT"));
        EnvioSunat envio = envioRepository.findByResumenId(anterior.getResumen().getId())
                .orElseThrow(() -> new IllegalStateException("La baja " + anterior.getResumen().getNumero() + " no tiene envío"));
        if (envio.getEstado() != EstadoSunat.RECHAZADO) {
            throw new BusinessException("La baja " + anterior.getResumen().getNumero() + " está " + envio.getEstado()
                    + (envio.getEstado() == EstadoSunat.ACEPTADO ? ": SUNAT ya la registró"
                    : ": envíala o consulta su resultado desde el panel en lugar de generar otra"));
        }
        Comprobante comprobante = anterior.getComprobante();
        validar(comprobante);
        registrar(comprobante, anterior.getMotivo());
        return envioSunatService.infoBaja(comprobante);
    }

    /**
     * Genera la comunicación de baja o el resumen diario del comprobante (en la transacción de la
     * anulación) y deja su envío pendiente, con el XML ya firmado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResumenSunat registrar(Comprobante c, String motivo) {
        resumenRepository.bloquearNumeracion();
        TipoResumen tipo = TipoResumen.paraSerie(c.getSerie());
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
        ResumenSunat resumen = new ResumenSunat();
        resumen.setTipo(tipo);
        resumen.setFechaGeneracion(hoy);
        resumen.setCorrelativo(resumenRepository.siguienteCorrelativo(tipo, hoy));
        resumen.setFechaReferencia(c.getFechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toLocalDate());
        resumen.agregar(c, motivo.length() <= ResumenSunatDetalle.MOTIVO_MAXIMO
                ? motivo : motivo.substring(0, ResumenSunatDetalle.MOTIVO_MAXIMO));
        resumenRepository.save(resumen);
        envioSunatService.registrar(resumen);
        return resumen;
    }
}
