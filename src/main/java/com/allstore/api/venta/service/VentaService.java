package com.allstore.api.venta.service;

import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.dto.VentaResumenResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.ComprobantePago;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VentaService {

    public static final String REFERENCIA_COMPROBANTE = "COMPROBANTE";

    /**
     * Por encima de este monto la boleta debe identificar al comprador (DNI u otro documento).
     * Es el límite vigente de SUNAT; si cambia, se cambia aquí.
     */
    public static final BigDecimal MONTO_BOLETA_SIN_IDENTIFICAR = new BigDecimal("700.00");

    private static final Set<TipoComprobante> TIPOS_VENTA =
            EnumSet.of(TipoComprobante.NOTA_VENTA, TipoComprobante.BOLETA, TipoComprobante.FACTURA);
    private static final Pattern NUMERO = Pattern.compile("^([A-Z0-9]{4})-(\\d{1,8})$");

    private final ComprobanteRepository comprobanteRepository;
    private final SerieService serieService;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final InventarioService inventarioService;

    // ---------- Emitir ----------

    /**
     * Registra la venta en una sola transacción: valida, calcula IGV, descuenta stock, toma el
     * número de la serie y guarda. Si cualquier paso falla, no queda nada (ni stock descontado
     * ni número consumido).
     */
    @Transactional
    public VentaResponse crear(VentaRequest request) {
        TipoComprobante tipo = request.tipoComprobante();
        if (!TIPOS_VENTA.contains(tipo)) {
            throw new BusinessException("Una venta se emite como NOTA_VENTA, BOLETA o FACTURA");
        }
        Serie serie = serieParaEmitir(request.serieId(), tipo);
        Cliente cliente = clienteParaVenta(request.clienteId());

        Comprobante comprobante = new Comprobante();
        comprobante.setTipoComprobante(tipo);
        comprobante.setSerieRef(serie);
        comprobante.setEstablecimiento(serie.getEstablecimiento());
        comprobante.asignarCliente(cliente);
        comprobante.setObservacion(limpiar(request.observacion()));
        agregarDetalles(comprobante, request.items());
        comprobante.recalcularTotales();
        if (comprobante.getTotal().signum() <= 0) {
            throw new BusinessException("El total de la venta debe ser mayor a cero");
        }
        validarCliente(tipo, cliente, comprobante.getTotal());
        agregarPagos(comprobante, request.pagos());

        // Primero el stock y después el número: así la fila de la serie (por la que pasan todas
        // las ventas del local) queda bloqueada el menor tiempo posible.
        List<MovimientoInventario> movimientos = inventarioService.registrar(
                serie.getEstablecimiento().getId(), MotivoMovimiento.VENTA,
                request.items().stream().map(i -> new InventarioService.Linea(i.varianteId(), i.cantidad())).toList(),
                null);
        emitir(comprobante, serie);
        referenciar(movimientos, comprobante);
        return VentaResponse.from(comprobante, null);
    }

    /**
     * Formaliza una nota de venta en boleta o factura: mismas líneas, precios y pagos. No mueve
     * stock (la mercadería ya salió con la nota de venta).
     */
    @Transactional
    public VentaResponse convertir(Long notaVentaId, ConvertirVentaRequest request) {
        Comprobante nota = obtener(notaVentaId);
        if (nota.getTipoComprobante() != TipoComprobante.NOTA_VENTA) {
            throw new BusinessException("Solo una nota de venta se puede convertir en boleta o factura");
        }
        exigirEmitido(nota);
        TipoComprobante tipo = request.tipoComprobante();
        if (tipo != TipoComprobante.BOLETA && tipo != TipoComprobante.FACTURA) {
            throw new BusinessException("La nota de venta se convierte en BOLETA o FACTURA");
        }
        Serie serie = serieParaEmitir(request.serieId(), tipo);
        if (!serie.getEstablecimiento().getId().equals(nota.getEstablecimiento().getId())) {
            throw new BusinessException("La serie " + serie.getCodigo() + " es de otro establecimiento; usa una de "
                    + nota.getEstablecimiento().getNombre() + ", donde se hizo la venta");
        }
        Cliente cliente = request.clienteId() != null ? clienteParaVenta(request.clienteId()) : nota.getCliente();
        validarCliente(tipo, cliente, nota.getTotal());

        Comprobante nuevo = new Comprobante();
        nuevo.setTipoComprobante(tipo);
        nuevo.setSerieRef(serie);
        nuevo.setEstablecimiento(nota.getEstablecimiento());
        nuevo.asignarCliente(cliente);
        nuevo.setObservacion(nota.getObservacion());
        nuevo.setComprobanteOrigen(nota);
        nota.getDetalles().forEach(d -> nuevo.agregarDetalle(d.copiar()));
        nota.getPagos().forEach(p -> nuevo.agregarPago(p.copiar()));
        nuevo.recalcularTotales();
        emitir(nuevo, serie);
        nota.setEstado(EstadoComprobante.CONVERTIDO);
        return VentaResponse.from(nuevo, null);
    }

    /**
     * Anula una nota de venta y devuelve la mercadería al stock. Las boletas y facturas no se
     * anulan así: ante SUNAT se corrigen con una nota de crédito.
     */
    @Transactional
    public VentaResponse anular(Long id, AnularVentaRequest request) {
        Comprobante comprobante = obtener(id);
        if (comprobante.getTipoComprobante() != TipoComprobante.NOTA_VENTA) {
            throw new BusinessException("Una " + comprobante.getTipoComprobante().getDescripcion().toLowerCase()
                    + " no se anula directamente: se corrige con una nota de crédito");
        }
        exigirEmitido(comprobante);
        List<MovimientoInventario> movimientos = inventarioService.registrar(
                comprobante.getEstablecimiento().getId(), MotivoMovimiento.ANULACION_VENTA,
                comprobante.getDetalles().stream()
                        .map(d -> new InventarioService.Linea(d.getVariante().getId(), d.getCantidad())).toList(),
                limpiar(request.motivo()));
        referenciar(movimientos, comprobante);
        comprobante.setEstado(EstadoComprobante.ANULADO);
        comprobante.setMotivoAnulacion(request.motivo().trim());
        comprobante.setFechaAnulacion(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        return VentaResponse.from(comprobante, null);
    }

    // ---------- Consultar ----------

    @Transactional(readOnly = true)
    public VentaResponse obtenerDetalle(Long id) {
        Comprobante comprobante = obtener(id);
        String convertidoEn = comprobanteRepository.findFirstByComprobanteOrigenIdOrderByIdDesc(id)
                .map(Comprobante::getNumeroImpreso).orElse(null);
        return VentaResponse.from(comprobante, convertidoEn);
    }

    /** La búsqueda acepta un número (B001-15 o B001-00000015), un documento o parte del nombre del cliente. */
    @Transactional(readOnly = true)
    public PageResponse<VentaResumenResponse> listar(TipoComprobante tipo, EstadoComprobante estado,
                                                     Long establecimientoId, LocalDate desde, LocalDate hasta,
                                                     String busqueda, Pageable pageable) {
        Specification<Comprobante> spec = Specification.unrestricted();
        if (tipo != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tipoComprobante"), tipo));
        }
        if (estado != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (establecimientoId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("establecimiento").get("id"), establecimientoId));
        }
        if (desde != null) {
            OffsetDateTime inicio = desde.atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fechaEmision"), inicio));
        }
        if (hasta != null) {
            OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("fechaEmision"), fin));
        }
        if (StringUtils.hasText(busqueda)) {
            String texto = busqueda.trim().toUpperCase();
            Matcher numero = NUMERO.matcher(texto);
            if (numero.matches()) {
                String serie = numero.group(1);
                long correlativo = Long.parseLong(numero.group(2));
                spec = spec.and((root, q, cb) -> cb.and(
                        cb.equal(root.get("serie"), serie), cb.equal(root.get("correlativo"), correlativo)));
            } else {
                String patron = "%" + texto.toLowerCase() + "%";
                spec = spec.and((root, q, cb) -> cb.or(
                        cb.like(cb.lower(root.get("clienteNombre")), patron),
                        cb.like(root.get("clienteNumeroDocumento"), patron)));
            }
        }
        return PageResponse.of(comprobanteRepository.findAll(spec, pageable).map(VentaResumenResponse::from));
    }

    // ---------- Reglas ----------

    private void agregarDetalles(Comprobante comprobante, List<VentaItemRequest> items) {
        Set<Long> vistas = new HashSet<>();
        items.forEach(i -> {
            if (!vistas.add(i.varianteId())) {
                throw new BusinessException("Una prenda aparece más de una vez; suma las cantidades en una sola línea");
            }
        });
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(vistas);
        for (VentaItemRequest item : items) {
            ProductoVariante variante = variantes.get(item.varianteId());
            if (!variante.isActivo() || !variante.getProducto().isActivo()) {
                throw new BusinessException("La prenda " + variante.getSku() + " está inactiva y no se puede vender");
            }
            BigDecimal precio = variante.getPrecioEfectivo();
            BigDecimal descuento = item.descuento() == null ? BigDecimal.ZERO : item.descuento();
            if (descuento.compareTo(precio.multiply(BigDecimal.valueOf(item.cantidad()))) >= 0) {
                throw new BusinessException("El descuento de " + variante.getSku() + " no puede ser igual o mayor que su importe");
            }
            String afectacion = variante.getProducto().getTipoAfectacionIgv();
            CalculoIgv.Linea calculo = CalculoIgv.calcular(item.cantidad(), precio, descuento, afectacion);

            ComprobanteDetalle detalle = new ComprobanteDetalle();
            detalle.setVariante(variante);
            detalle.setSku(variante.getSku());
            detalle.setDescripcion(variante.getProducto().getNombre() + " - " + variante.getColor().getNombre()
                    + " talla " + variante.getTalla().getCodigo());
            detalle.setUnidadMedida(variante.getProducto().getUnidadMedida());
            detalle.setTipoAfectacionIgv(afectacion);
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(precio);
            detalle.setDescuento(descuento.setScale(2));
            detalle.setValorVenta(calculo.valorVenta());
            detalle.setIgv(calculo.igv());
            detalle.setTotal(calculo.total());
            comprobante.agregarDetalle(detalle);
        }
    }

    /** Los pagos deben sumar exactamente el total. Solo el efectivo admite vuelto. */
    private void agregarPagos(Comprobante comprobante, List<PagoRequest> pagos) {
        BigDecimal suma = BigDecimal.ZERO;
        for (PagoRequest p : pagos) {
            ComprobantePago pago = new ComprobantePago();
            pago.setMetodo(p.metodo());
            pago.setMonto(p.monto().setScale(2));
            pago.setReferencia(limpiar(p.referencia()));
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
            throw new BusinessException("Los pagos suman S/ " + suma + " pero el total de la venta es S/ "
                    + comprobante.getTotal());
        }
    }

    /**
     * Factura: solo a un cliente con RUC. Boleta: por encima de S/ 700 hay que identificar al
     * comprador. Nota de venta: sin restricciones (es un documento interno).
     */
    private void validarCliente(TipoComprobante tipo, Cliente cliente, BigDecimal total) {
        if (tipo == TipoComprobante.FACTURA && cliente.getTipoDocumento() != TipoDocumentoIdentidad.RUC) {
            throw new BusinessException("La factura requiere un cliente con RUC; a una persona se le emite boleta");
        }
        if (tipo == TipoComprobante.BOLETA && cliente.isGenerico()
                && total.compareTo(MONTO_BOLETA_SIN_IDENTIFICAR) > 0) {
            throw new BusinessException("Para boletas mayores a S/ " + MONTO_BOLETA_SIN_IDENTIFICAR
                    + " identifica al cliente con su DNI u otro documento");
        }
    }

    private Serie serieParaEmitir(Long serieId, TipoComprobante tipo) {
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

    private Cliente clienteParaVenta(Long clienteId) {
        Cliente cliente = clienteId == null ? clienteService.obtenerGenericoEntidad() : clienteService.obtenerEntidad(clienteId);
        if (!cliente.isActivo()) {
            throw new BusinessException("El cliente " + cliente.getNombre() + " está inactivo");
        }
        return cliente;
    }

    /** Toma el número de la serie (bloqueándola) y guarda el comprobante. */
    private void emitir(Comprobante comprobante, Serie serie) {
        NumeroComprobante numero = serieService.asignarSiguienteNumero(serie.getId(), comprobante.getTipoComprobante());
        comprobante.setSerie(numero.serie());
        comprobante.setCorrelativo(numero.correlativo());
        comprobante.setFechaEmision(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        comprobanteRepository.save(comprobante);
    }

    private void referenciar(List<MovimientoInventario> movimientos, Comprobante comprobante) {
        String documento = comprobante.getTipoComprobante().getDescripcion() + " " + comprobante.getNumeroImpreso();
        movimientos.forEach(m -> m.referenciar(REFERENCIA_COMPROBANTE, comprobante.getId(), documento));
    }

    private static void exigirEmitido(Comprobante comprobante) {
        switch (comprobante.getEstado()) {
            case ANULADO -> throw new BusinessException("El comprobante " + comprobante.getNumeroImpreso() + " ya está anulado");
            case CONVERTIDO -> throw new BusinessException("La nota de venta " + comprobante.getNumeroImpreso()
                    + " ya se convirtió en boleta o factura; es ese comprobante el que se debe corregir");
            case EMITIDO -> {
            }
        }
    }

    private Comprobante obtener(Long id) {
        return comprobanteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
