package com.allstore.api.venta.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.venta.dto.FiltroVentas;
import com.allstore.api.venta.dto.TotalesVentasResponse;
import com.allstore.api.venta.dto.VentaResumenResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobantePago;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.repository.ComprobanteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Listado y totales de ventas con los mismos filtros (ver {@link FiltroVentas}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaVentasService {

    private static final Pattern NUMERO = Pattern.compile("^([A-Z0-9]{4})-(\\d{1,8})$");
    private static final Set<TipoComprobante> TIPOS_VENTA =
            EnumSet.of(TipoComprobante.NOTA_VENTA, TipoComprobante.BOLETA, TipoComprobante.FACTURA);

    private final ComprobanteRepository comprobanteRepository;
    private final EnvioSunatService envioSunatService;

    @PersistenceContext
    private EntityManager entityManager;

    public PageResponse<VentaResumenResponse> listar(FiltroVentas filtro, Pageable pageable) {
        validar(filtro);
        Specification<Comprobante> spec = (root, query, cb) -> predicado(filtro, root, query, cb);
        Page<Comprobante> pagina = comprobanteRepository.findAll(spec, pageable);
        List<Long> ids = pagina.map(Comprobante::getId).getContent();
        Map<Long, List<MetodoPago>> metodos = metodosDePago(ids);
        Map<Long, EstadoSunat> estados = envioSunatService.estados(ids);
        return PageResponse.of(pagina.map(c -> VentaResumenResponse.from(c,
                metodos.getOrDefault(c.getId(), List.of()), estados.get(c.getId()))));
    }

    /** Solo cuenta lo vigente: ni los anulados ni las notas de venta ya convertidas en boleta o factura. */
    public TotalesVentasResponse totales(FiltroVentas filtro) {
        validar(filtro);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Tuple> porTipo = cb.createTupleQuery();
        Root<Comprobante> c = porTipo.from(Comprobante.class);
        porTipo.multiselect(c.get("tipoComprobante"), c.get("estado"), cb.count(c), cb.sum(c.<BigDecimal>get("total")))
                .where(predicado(filtro, c, porTipo, cb))
                .groupBy(c.get("tipoComprobante"), c.get("estado"));
        Map<TipoComprobante, Long> cantidades = new EnumMap<>(TipoComprobante.class);
        Map<TipoComprobante, BigDecimal> montos = new EnumMap<>(TipoComprobante.class);
        long anulados = 0;
        for (Tuple fila : entityManager.createQuery(porTipo).getResultList()) {
            TipoComprobante tipo = fila.get(0, TipoComprobante.class);
            EstadoComprobante estado = fila.get(1, EstadoComprobante.class);
            long cantidad = fila.get(2, Long.class);
            if (estado == EstadoComprobante.ANULADO) {
                anulados += cantidad;
            } else if (estado == EstadoComprobante.EMITIDO) {
                cantidades.merge(tipo, cantidad, Long::sum);
                montos.merge(tipo, fila.get(3, BigDecimal.class), BigDecimal::add);
            }
        }

        CriteriaQuery<Tuple> porMetodo = cb.createTupleQuery();
        Root<ComprobantePago> pago = porMetodo.from(ComprobantePago.class);
        Join<ComprobantePago, Comprobante> pagado = pago.join("comprobante");
        porMetodo.multiselect(pagado.get("tipoComprobante"), pago.get("metodo"), cb.sum(pago.<BigDecimal>get("monto")))
                .where(predicado(filtro, pagado, porMetodo, cb), cb.equal(pagado.get("estado"), EstadoComprobante.EMITIDO))
                .groupBy(pagado.get("tipoComprobante"), pago.get("metodo"));
        Map<MetodoPago, BigDecimal> cobrado = new EnumMap<>(MetodoPago.class);
        Map<MetodoPago, BigDecimal> devuelto = new EnumMap<>(MetodoPago.class);
        for (Tuple fila : entityManager.createQuery(porMetodo).getResultList()) {
            Map<MetodoPago, BigDecimal> destino = fila.get(0, TipoComprobante.class) == TipoComprobante.NOTA_CREDITO
                    ? devuelto : cobrado;
            destino.merge(fila.get(1, MetodoPago.class), fila.get(2, BigDecimal.class), BigDecimal::add);
        }

        BigDecimal ventas = sumar(montos, TIPOS_VENTA);
        BigDecimal notasCredito = sumar(montos, Set.of(TipoComprobante.NOTA_CREDITO));
        BigDecimal notasDebito = sumar(montos, Set.of(TipoComprobante.NOTA_DEBITO));
        cobrado.replaceAll((metodo, monto) -> monto.setScale(2));
        devuelto.replaceAll((metodo, monto) -> monto.setScale(2));
        return new TotalesVentasResponse(
                TIPOS_VENTA.stream().mapToLong(t -> cantidades.getOrDefault(t, 0L)).sum(), ventas,
                cantidades.getOrDefault(TipoComprobante.NOTA_CREDITO, 0L), notasCredito,
                cantidades.getOrDefault(TipoComprobante.NOTA_DEBITO, 0L), notasDebito,
                ventas.subtract(notasCredito).add(notasDebito), cobrado, devuelto, anulados);
    }

    // ---------- Filtros ----------

    /**
     * Recibe un {@link From} y no solo la raíz para servir también cuando el comprobante llega por
     * un join (los pagos por método se agrupan desde comprobante_pago).
     */
    private static Predicate predicado(FiltroVentas f, From<?, Comprobante> c, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> condiciones = new ArrayList<>();
        if (hay(f.tipoComprobante())) {
            condiciones.add(c.get("tipoComprobante").in(f.tipoComprobante()));
        }
        if (hay(f.estado())) {
            condiciones.add(c.get("estado").in(f.estado()));
        }
        if (f.establecimientoId() != null) {
            condiciones.add(cb.equal(c.get("establecimiento").get("id"), f.establecimientoId()));
        }
        if (f.desde() != null) {
            condiciones.add(cb.greaterThanOrEqualTo(c.get("fechaEmision"), inicioDelDia(f.desde())));
        }
        if (f.hasta() != null) {
            condiciones.add(cb.lessThan(c.get("fechaEmision"), inicioDelDia(f.hasta().plusDays(1))));
        }
        if (hay(f.metodoPago())) {
            Subquery<Long> pagos = query.subquery(Long.class);
            Root<ComprobantePago> pago = pagos.from(ComprobantePago.class);
            pagos.select(pago.get("id")).where(cb.equal(pago.get("comprobante").get("id"), c.get("id")),
                    pago.get("metodo").in(f.metodoPago()));
            condiciones.add(cb.exists(pagos));
        }
        if (f.porMayor() != null) {
            condiciones.add(cb.equal(c.get("porMayor"), f.porMayor()));
        }
        if (f.porMayorManual() != null) {
            condiciones.add(cb.equal(c.get("porMayorManual"), f.porMayorManual()));
        }
        if (StringUtils.hasText(f.vendedor())) {
            condiciones.add(cb.equal(cb.lower(c.get("createdBy")), f.vendedor().trim().toLowerCase()));
        }
        if (f.clienteId() != null) {
            condiciones.add(cb.equal(c.get("cliente").get("id"), f.clienteId()));
        }
        if (f.cajaSesionId() != null) {
            condiciones.add(cb.equal(c.get("cajaSesion").get("id"), f.cajaSesionId()));
        }
        if (hay(f.estadoSunat())) {
            Subquery<Long> envios = query.subquery(Long.class);
            Root<EnvioSunat> envio = envios.from(EnvioSunat.class);
            envios.select(envio.get("id")).where(cb.equal(envio.get("comprobante").get("id"), c.get("id")),
                    envio.get("estado").in(f.estadoSunat()));
            condiciones.add(cb.exists(envios));
        }
        if (f.totalDesde() != null) {
            condiciones.add(cb.greaterThanOrEqualTo(c.get("total"), f.totalDesde()));
        }
        if (f.totalHasta() != null) {
            condiciones.add(cb.lessThanOrEqualTo(c.get("total"), f.totalHasta()));
        }
        if (StringUtils.hasText(f.busqueda())) {
            condiciones.add(busqueda(f.busqueda(), c, cb));
        }
        return cb.and(condiciones.toArray(Predicate[]::new));
    }

    /** Un número (B001-15 o B001-00000015), un documento o parte del nombre del cliente. */
    private static Predicate busqueda(String busqueda, From<?, Comprobante> c, CriteriaBuilder cb) {
        String texto = busqueda.trim().toUpperCase();
        Matcher numero = NUMERO.matcher(texto);
        if (numero.matches()) {
            return cb.and(cb.equal(c.get("serie"), numero.group(1)),
                    cb.equal(c.get("correlativo"), Long.parseLong(numero.group(2))));
        }
        String patron = "%" + texto.toLowerCase() + "%";
        return cb.or(cb.like(cb.lower(c.get("clienteNombre")), patron), cb.like(c.get("clienteNumeroDocumento"), patron));
    }

    private static void validar(FiltroVentas f) {
        if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
            throw new BusinessException("La fecha desde (" + f.desde() + ") es posterior a la fecha hasta (" + f.hasta() + ")");
        }
        if (f.totalDesde() != null && f.totalHasta() != null && f.totalDesde().compareTo(f.totalHasta()) > 0) {
            throw new BusinessException("El total desde (" + f.totalDesde() + ") es mayor que el total hasta ("
                    + f.totalHasta() + ")");
        }
    }

    // ---------- Ayudas ----------

    private Map<Long, List<MetodoPago>> metodosDePago(List<Long> ids) {
        Map<Long, List<MetodoPago>> metodos = new HashMap<>();
        if (!ids.isEmpty()) {
            comprobanteRepository.metodosDePago(ids).forEach(m -> {
                List<MetodoPago> delComprobante = metodos.computeIfAbsent(m.getComprobanteId(), id -> new ArrayList<>());
                if (!delComprobante.contains(m.getMetodo())) {
                    delComprobante.add(m.getMetodo());
                }
            });
        }
        return metodos;
    }

    private static OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
    }

    private static BigDecimal sumar(Map<TipoComprobante, BigDecimal> montos, Set<TipoComprobante> tipos) {
        return tipos.stream().map(t -> montos.getOrDefault(t, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    private static boolean hay(Collection<?> valores) {
        return valores != null && !valores.isEmpty();
    }
}
