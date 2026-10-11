package com.allstore.api.caja.service;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.dto.CajaResponse;
import com.allstore.api.caja.dto.CajaSesionResumenResponse;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.caja.dto.MovimientoCajaRequest;
import com.allstore.api.caja.dto.ResumenCaja;
import com.allstore.api.caja.entity.CajaMovimiento;
import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.entity.EstadoCaja;
import com.allstore.api.caja.entity.TipoMovimientoCaja;
import com.allstore.api.caja.repository.CajaMovimientoRepository;
import com.allstore.api.caja.repository.CajaSesionRepository;
import com.allstore.api.caja.repository.ResumenCajaRepository;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CajaService {

    private static final Set<TipoComprobante> TIPOS_VENTA =
            EnumSet.of(TipoComprobante.NOTA_VENTA, TipoComprobante.BOLETA, TipoComprobante.FACTURA);
    private static final Set<TipoComprobante> TIPOS_DEVOLUCION = EnumSet.of(TipoComprobante.NOTA_CREDITO);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final CajaSesionRepository sesionRepository;
    private final CajaMovimientoRepository movimientoRepository;
    private final ResumenCajaRepository resumenRepository;
    private final EstablecimientoService establecimientoService;
    private final AuditorAware<String> usuarioActual;

    @Transactional
    public CajaResponse abrir(AbrirCajaRequest request) {
        String usuario = usuario();
        sesionRepository.findByUsuarioAndEstado(usuario, EstadoCaja.ABIERTA).ifPresent(abierta -> {
            throw new BusinessException("Ya tienes una caja abierta en " + abierta.getEstablecimiento().getNombre()
                    + " desde el " + HORA.format(abierta.getFechaApertura()) + "; ciérrala antes de abrir otra");
        });
        Establecimiento establecimiento = establecimientoService.obtener(request.establecimientoId());
        if (!establecimiento.isActivo()) {
            throw new BusinessException("El establecimiento " + establecimiento.getNombre() + " está inactivo");
        }
        CajaSesion sesion = new CajaSesion();
        sesion.setEstablecimiento(establecimiento);
        sesion.setUsuario(usuario);
        sesion.setFechaApertura(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        sesion.setMontoApertura(request.montoApertura().setScale(2));
        sesionRepository.save(sesion);
        return respuesta(sesion);
    }

    /** La caja abierta del usuario, con su cuadre al momento. */
    @Transactional(readOnly = true)
    public CajaResponse miCaja() {
        CajaSesion sesion = sesionRepository.findByUsuarioAndEstado(usuario(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una caja abierta"));
        return respuesta(sesion);
    }

    @Transactional
    public CajaResponse registrarMovimiento(MovimientoCajaRequest request) {
        CajaSesion sesion = bloquearMiCaja();
        CajaMovimiento movimiento = new CajaMovimiento();
        movimiento.setCajaSesion(sesion);
        movimiento.setTipo(request.tipo());
        movimiento.setMonto(request.monto().setScale(2));
        movimiento.setConcepto(request.concepto().trim());
        movimientoRepository.save(movimiento);
        return respuesta(sesion);
    }

    /** Calcula el cuadre, lo guarda tal cual (ya no cambia) y cierra la caja. */
    @Transactional
    public CajaResponse cerrar(CerrarCajaRequest request) {
        CajaSesion sesion = bloquearMiCaja();
        ResumenCaja resumen = calcular(sesion);
        BigDecimal contado = request.montoContado().setScale(2);
        sesion.setTotalVentas(resumen.totalVentas());
        sesion.setTotalNotasCredito(resumen.totalNotasCredito());
        sesion.setTotalIngresos(resumen.totalIngresos());
        sesion.setTotalEgresos(resumen.totalEgresos());
        sesion.setEfectivoEsperado(resumen.efectivoEsperado());
        sesion.setEfectivoContado(contado);
        sesion.setDiferencia(contado.subtract(resumen.efectivoEsperado()));
        sesion.setObservacionCierre(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);
        sesion.setEstado(EstadoCaja.CERRADA);
        sesion.setFechaCierre(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        return CajaResponse.from(sesion, resumen, movimientoRepository.findByCajaSesionIdOrderById(sesion.getId()));
    }

    /** Historial de cajas para el administrador. */
    @Transactional(readOnly = true)
    public PageResponse<CajaSesionResumenResponse> listar(Long establecimientoId, String usuario, EstadoCaja estado,
                                                          LocalDate desde, LocalDate hasta, Pageable pageable) {
        Specification<CajaSesion> spec = Specification.unrestricted();
        if (establecimientoId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("establecimiento").get("id"), establecimientoId));
        }
        if (StringUtils.hasText(usuario)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("usuario"), usuario.trim().toLowerCase()));
        }
        if (estado != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (desde != null) {
            OffsetDateTime inicio = desde.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fechaApertura"), inicio));
        }
        if (hasta != null) {
            OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("fechaApertura"), fin));
        }
        return PageResponse.of(sesionRepository.findAll(spec, pageable).map(CajaSesionResumenResponse::from));
    }

    @Transactional(readOnly = true)
    public CajaResponse obtener(Long id) {
        return respuesta(sesionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Caja", id)));
    }

    /**
     * Para ventas y notas de crédito: la caja abierta del usuario, bloqueada, que debe ser del
     * mismo establecimiento que la serie con la que se emite. Sin caja abierta no se cobra ni se
     * devuelve dinero.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CajaSesion bloquearCajaPara(Long establecimientoId) {
        CajaSesion sesion = bloquearMiCaja();
        if (!sesion.getEstablecimiento().getId().equals(establecimientoId)) {
            throw new BusinessException("Tu caja abierta es de " + sesion.getEstablecimiento().getNombre()
                    + "; usa una serie de ese local o abre caja en el local de la serie");
        }
        return sesion;
    }

    private CajaSesion bloquearMiCaja() {
        return sesionRepository.bloquearAbierta(usuario())
                .orElseThrow(() -> new BusinessException("Abre tu caja antes de registrar ventas, devoluciones o movimientos"));
    }

    private CajaResponse respuesta(CajaSesion sesion) {
        return CajaResponse.from(sesion, calcular(sesion), movimientoRepository.findByCajaSesionIdOrderById(sesion.getId()));
    }

    /**
     * Cuadre de la caja. Los comprobantes anulados no cuentan (el dinero se devolvió en la misma
     * caja, que debía seguir abierta para poder anular).
     */
    private ResumenCaja calcular(CajaSesion sesion) {
        Long id = sesion.getId();
        ResumenCajaRepository.Totales ventas = resumenRepository.totales(id, TIPOS_VENTA, EstadoComprobante.ANULADO);
        ResumenCajaRepository.Totales devoluciones = resumenRepository.totales(id, TIPOS_DEVOLUCION, EstadoComprobante.ANULADO);
        Map<MetodoPago, BigDecimal> cobros = porMetodo(id, TIPOS_VENTA);
        Map<MetodoPago, BigDecimal> reembolsos = porMetodo(id, TIPOS_DEVOLUCION);
        BigDecimal ingresos = cero(movimientoRepository.total(id, TipoMovimientoCaja.INGRESO));
        BigDecimal egresos = cero(movimientoRepository.total(id, TipoMovimientoCaja.EGRESO));
        BigDecimal efectivoEsperado = sesion.getMontoApertura()
                .add(cobros.getOrDefault(MetodoPago.EFECTIVO, BigDecimal.ZERO))
                .subtract(reembolsos.getOrDefault(MetodoPago.EFECTIVO, BigDecimal.ZERO))
                .add(ingresos)
                .subtract(egresos);
        return new ResumenCaja(sesion.getMontoApertura(), ventas.getCantidad(), cero(ventas.getTotal()), cobros,
                devoluciones.getCantidad(), cero(devoluciones.getTotal()), reembolsos, ingresos, egresos,
                efectivoEsperado.setScale(2));
    }

    private Map<MetodoPago, BigDecimal> porMetodo(Long cajaId, Set<TipoComprobante> tipos) {
        Map<MetodoPago, BigDecimal> mapa = new EnumMap<>(MetodoPago.class);
        resumenRepository.pagosPorMetodo(cajaId, tipos, EstadoComprobante.ANULADO)
                .forEach(f -> mapa.put(f.getMetodo(), f.getMonto()));
        return mapa;
    }

    private String usuario() {
        return usuarioActual.getCurrentAuditor().orElse(JpaAuditingConfig.USUARIO_SISTEMA);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor.setScale(2);
    }
}
