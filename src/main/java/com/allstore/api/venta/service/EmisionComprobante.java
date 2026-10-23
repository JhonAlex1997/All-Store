package com.allstore.api.venta.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.facturacion.dto.BajaSunatInfo;
import com.allstore.api.facturacion.dto.SunatInfo;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobantePago;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pasos comunes a todo comprobante (venta, conversión, notas de crédito y débito): reconocer los
 * reintentos, validar la serie, registrar los pagos, tomar el número, guardar y enlazar los
 * movimientos de inventario con el documento.
 */
@Component
@RequiredArgsConstructor
public class EmisionComprobante {

    public static final String REFERENCIA_COMPROBANTE = "COMPROBANTE";
    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final ComprobanteRepository comprobanteRepository;
    private final SerieService serieService;
    private final EnvioSunatService envioSunatService;
    private final JsonMapper json;

    /**
     * La solicitud con su clave Idempotency-Key (o NINGUNA si la caja no la envió) y la huella de
     * la operación y sus datos.
     */
    public SolicitudIdempotente solicitud(String clave, String operacion, Object... datos) {
        if (!StringUtils.hasText(clave)) {
            return SolicitudIdempotente.NINGUNA;
        }
        String limpia = clave.trim();
        if (!CLAVE.matcher(limpia).matches()) {
            throw new BusinessException("La cabecera " + SolicitudIdempotente.CABECERA + " debe tener de 8 a 64 caracteres "
                    + "(letras, números, - o _); lo usual es un UUID nuevo por cada venta");
        }
        List<Object> contenido = new ArrayList<>();
        contenido.add(operacion);
        contenido.addAll(Arrays.asList(datos));
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(contenido));
            return new SolicitudIdempotente(limpia, HexFormat.of().formatHex(sha));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * Si la solicitud ya se atendió (reintento con la misma clave), el comprobante que se emitió
     * entonces, para devolverlo en vez de emitir otro: así una venta nunca se registra dos veces
     * aunque la respuesta no llegue a la caja o el cajero haga doble clic. Va al inicio de la
     * transacción, antes de validar (el reintento de una conversión ya no la encontraría vigente).
     * La misma clave con otro contenido es un error de la caja.
     */
    public Optional<VentaResponse> yaAtendida(SolicitudIdempotente solicitud) {
        if (!solicitud.presente()) {
            return Optional.empty();
        }
        comprobanteRepository.bloquearClave(solicitud.clave());
        return comprobanteRepository.findByClaveIdempotencia(solicitud.clave()).map(previo -> {
            if (!solicitud.huella().equals(previo.getHuellaSolicitud())) {
                throw new BusinessException("La clave " + SolicitudIdempotente.CABECERA + " " + solicitud.clave()
                        + " ya se usó para otra solicitud (" + previo.getTipoComprobante().getDescripcion() + " "
                        + previo.getNumeroImpreso() + "); usa una clave nueva por cada venta");
            }
            return respuesta(previo, null, List.of());
        });
    }

    /** La serie debe ser del tipo pedido y estar activa, igual que su establecimiento. */
    public Serie serieParaEmitir(Long serieId, TipoComprobante tipo) {
        Serie serie = serieService.obtener(serieId);
        if (serie.getTipoComprobante() != tipo) {
            throw new BusinessException("La serie " + serie.getCodigo() + " es de "
                    + serie.getTipoComprobante().getDescripcion().toLowerCase() + ", no de "
                    + tipo.getDescripcion().toLowerCase());
        }
        if (!serie.isActivo()) {
            throw new BusinessException("La serie " + serie.getCodigo() + " está inactiva");
        }
        if (!serie.getEstablecimiento().isActivo()) {
            throw new BusinessException("El establecimiento de la serie " + serie.getCodigo() + " está inactivo");
        }
        return serie;
    }

    /**
     * Toma el número de la serie (bloqueándola), guarda el comprobante y, si es electrónico,
     * registra su envío a SUNAT (con el XML ya firmado). Va al final de la transacción.
     */
    public void emitir(Comprobante comprobante, Serie serie) {
        emitir(comprobante, serie, SolicitudIdempotente.NINGUNA);
    }

    /** Igual, guardando la clave de la solicitud para reconocer sus reintentos. */
    public void emitir(Comprobante comprobante, Serie serie, SolicitudIdempotente solicitud) {
        comprobante.setClaveIdempotencia(solicitud.clave());
        comprobante.setHuellaSolicitud(solicitud.huella());
        NumeroComprobante numero = serieService.asignarSiguienteNumero(serie.getId(), comprobante.getTipoComprobante());
        comprobante.setSerie(numero.serie());
        comprobante.setCorrelativo(numero.correlativo());
        comprobante.setFechaEmision(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        comprobanteRepository.save(comprobante);
        envioSunatService.registrar(comprobante);
    }

    /** Respuesta completa del comprobante, con su estado ante SUNAT (y el de su baja, si se anuló). */
    public VentaResponse respuesta(Comprobante comprobante, String convertidoEn, List<String> notasCredito) {
        return respuesta(comprobante, convertidoEn, notasCredito, List.of());
    }

    public VentaResponse respuesta(Comprobante comprobante, String convertidoEn, List<String> notasCredito,
                                   List<String> notasDebito) {
        BajaSunatInfo baja = comprobante.getEstado() == EstadoComprobante.ANULADO
                && comprobante.getTipoComprobante().isElectronico() ? envioSunatService.infoBaja(comprobante) : null;
        String reemplazadoPor = comprobante.getEstado() == EstadoComprobante.REEMPLAZADO
                ? comprobanteRepository.findByReemplazaAId(comprobante.getId()).map(Comprobante::getNumeroImpreso).orElse(null)
                : null;
        return VentaResponse.from(comprobante, convertidoEn, reemplazadoPor, notasCredito, notasDebito,
                envioSunatService.info(comprobante), baja);
    }

    /** Estado del envío a SUNAT; null si no es electrónico. */
    public SunatInfo sunat(Comprobante comprobante) {
        return envioSunatService.info(comprobante);
    }

    /** SUNAT rechazó el comprobante: no tiene validez (se reemite con otro número o se anula). */
    public boolean rechazadoPorSunat(Comprobante comprobante) {
        SunatInfo sunat = sunat(comprobante);
        return sunat != null && sunat.estado() == EstadoSunat.RECHAZADO;
    }

    /** Una nota no puede modificar un comprobante que para SUNAT no existe: también la rechazaría. */
    public void exigirNoRechazado(Comprobante origen) {
        if (rechazadoPorSunat(origen)) {
            throw new BusinessException("SUNAT rechazó " + origen.getNumeroImpreso()
                    + ": no tiene validez y no admite notas. Reemítelo con otro número o anúlalo");
        }
    }

    /** Los pagos deben sumar exactamente el total. Solo el efectivo admite vuelto. */
    public void agregarPagos(Comprobante comprobante, List<PagoRequest> pagos) {
        BigDecimal suma = BigDecimal.ZERO;
        for (PagoRequest p : pagos) {
            ComprobantePago pago = new ComprobantePago();
            pago.setMetodo(p.metodo());
            pago.setMonto(p.monto().setScale(2));
            pago.setReferencia(StringUtils.hasText(p.referencia()) ? p.referencia().trim() : null);
            if (p.montoRecibido() != null) {
                if (p.metodo() != MetodoPago.EFECTIVO) {
                    throw new BusinessException("El monto recibido y el vuelto solo aplican a pagos en efectivo");
                }
                if (p.montoRecibido().compareTo(p.monto()) < 0) {
                    throw new BusinessException("El monto recibido (S/ " + p.montoRecibido()
                            + ") es menor que el pago en efectivo (S/ " + p.monto() + ")");
                }
                pago.setMontoRecibido(p.montoRecibido().setScale(2));
                pago.setVuelto(p.montoRecibido().subtract(p.monto()).setScale(2));
            }
            suma = suma.add(pago.getMonto());
            comprobante.agregarPago(pago);
        }
        if (suma.compareTo(comprobante.getTotal()) != 0) {
            throw new BusinessException("Los pagos suman S/ " + suma + " pero el total es S/ "
                    + comprobante.getTotal());
        }
    }

    public void referenciar(List<MovimientoInventario> movimientos, Comprobante comprobante) {
        String documento = comprobante.getTipoComprobante().getDescripcion() + " " + comprobante.getNumeroImpreso();
        movimientos.forEach(m -> m.referenciar(REFERENCIA_COMPROBANTE, comprobante.getId(), documento));
    }

    public Comprobante obtener(Long id) {
        return comprobanteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
    }
}
