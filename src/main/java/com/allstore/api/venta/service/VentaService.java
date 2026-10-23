package com.allstore.api.venta.service;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VentaService {

    /**
     * Por encima de este monto la boleta debe identificar al comprador (DNI u otro documento).
     * Es el límite vigente de SUNAT; si cambia, se cambia aquí.
     */
    public static final BigDecimal MONTO_BOLETA_SIN_IDENTIFICAR = new BigDecimal("700.00");

    private static final Set<TipoComprobante> TIPOS_VENTA =
            EnumSet.of(TipoComprobante.NOTA_VENTA, TipoComprobante.BOLETA, TipoComprobante.FACTURA);

    private final ComprobanteRepository comprobanteRepository;
    private final EmisionComprobante emision;
    private final ClienteService clienteService;
    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final CajaService cajaService;
    private final EmpresaService empresaService;

    // ---------- Emitir ----------

    /**
     * Registra la venta en una sola transacción: valida, calcula IGV, descuenta stock, toma el
     * número de la serie y guarda. Si cualquier paso falla, no queda nada (ni stock descontado
     * ni número consumido).
     *
     * <p>Orden de bloqueo, igual en ventas y notas de crédito: caja, stock y por último la serie
     * (la fila más disputada, que así queda bloqueada el menor tiempo).
     */
    @Transactional
    public VentaResponse crear(VentaRequest request) {
        TipoComprobante tipo = request.tipoComprobante();
        if (!TIPOS_VENTA.contains(tipo)) {
            throw new BusinessException("Una venta se emite como NOTA_VENTA, BOLETA o FACTURA");
        }
        Serie serie = emision.serieParaEmitir(request.serieId(), tipo);
        Cliente cliente = clienteParaVenta(request.clienteId());
        CajaSesion caja = cajaService.bloquearCajaPara(serie.getEstablecimiento().getId());

        Comprobante comprobante = new Comprobante();
        comprobante.setTipoComprobante(tipo);
        comprobante.setSerieRef(serie);
        comprobante.setEstablecimiento(serie.getEstablecimiento());
        comprobante.setCajaSesion(caja);
        comprobante.asignarCliente(cliente);
        comprobante.setObservacion(limpiar(request.observacion()));
        aplicarPrecioPorMayor(comprobante, request);
        agregarDetalles(comprobante, request.items());
        comprobante.recalcularTotales();
        if (comprobante.getTotal().signum() <= 0) {
            throw new BusinessException("El total de la venta debe ser mayor a cero");
        }
        validarCliente(tipo, cliente, comprobante.getTotal());
        emision.agregarPagos(comprobante, request.pagos());

        List<MovimientoInventario> movimientos = inventarioService.registrar(
                serie.getEstablecimiento().getId(), MotivoMovimiento.VENTA,
                request.items().stream().map(i -> new InventarioService.Linea(i.varianteId(), i.cantidad())).toList(),
                null);
        emision.emitir(comprobante, serie);
        emision.referenciar(movimientos, comprobante);
        return emision.respuesta(comprobante, null, List.of());
    }

    /**
     * Formaliza una nota de venta en boleta o factura: mismas líneas, precios y pagos. No mueve
     * stock (la mercadería ya salió con la nota de venta) ni pertenece a una caja (no hay un cobro
     * nuevo: el dinero ya entró con la nota de venta).
     */
    @Transactional
    public VentaResponse convertir(Long notaVentaId, ConvertirVentaRequest request) {
        Comprobante nota = emision.obtener(notaVentaId);
        if (nota.getTipoComprobante() != TipoComprobante.NOTA_VENTA) {
            throw new BusinessException("Solo una nota de venta se puede convertir en boleta o factura");
        }
        exigirEmitido(nota);
        TipoComprobante tipo = request.tipoComprobante();
        if (tipo != TipoComprobante.BOLETA && tipo != TipoComprobante.FACTURA) {
            throw new BusinessException("La nota de venta se convierte en BOLETA o FACTURA");
        }
        Serie serie = emision.serieParaEmitir(request.serieId(), tipo);
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
        nuevo.setPorMayor(nota.isPorMayor());
        nuevo.setPorMayorManual(nota.isPorMayorManual());
        nota.getDetalles().forEach(d -> nuevo.agregarDetalle(d.copiar()));
        nota.getPagos().forEach(p -> nuevo.agregarPago(p.copiar()));
        nuevo.recalcularTotales();
        emision.emitir(nuevo, serie);
        nota.setEstado(EstadoComprobante.CONVERTIDO);
        return emision.respuesta(nuevo, null, List.of());
    }

    // ---------- Consultar ----------

    /**
     * Incluye los documentos relacionados: la boleta o factura en que se convirtió una nota de
     * venta, o las notas de crédito y de débito emitidas sobre una boleta o factura.
     */
    @Transactional(readOnly = true)
    public VentaResponse obtenerDetalle(Long id) {
        Comprobante comprobante = emision.obtener(id);
        String convertidoEn = null;
        List<String> notasCredito = List.of();
        List<String> notasDebito = List.of();
        TipoComprobante tipo = comprobante.getTipoComprobante();
        if (tipo == TipoComprobante.NOTA_VENTA) {
            convertidoEn = comprobanteRepository.findFirstByComprobanteOrigenIdOrderByIdDesc(id)
                    .map(Comprobante::getNumeroImpreso).orElse(null);
        } else if (tipo == TipoComprobante.BOLETA || tipo == TipoComprobante.FACTURA) {
            List<Comprobante> notas = comprobanteRepository.notasVigentesDe(id);
            notasCredito = numeros(notas, TipoComprobante.NOTA_CREDITO);
            notasDebito = numeros(notas, TipoComprobante.NOTA_DEBITO);
        }
        return emision.respuesta(comprobante, convertidoEn, notasCredito, notasDebito);
    }

    // ---------- Reglas ----------

    /**
     * Precio por mayor: si la venta suma el mínimo de prendas de la empresa (de cualquier modelo,
     * talla o color), o si el cajero lo marca aunque sean menos (queda registrado como manual).
     */
    private void aplicarPrecioPorMayor(Comprobante comprobante, VentaRequest request) {
        int prendas = request.items().stream().mapToInt(VentaItemRequest::cantidad).sum();
        int minimo = empresaService.buscarEntidad().map(Empresa::getMinimoPrendasPorMayor).orElse(Empresa.MINIMO_PRENDAS_POR_MAYOR);
        boolean porCantidad = prendas >= minimo;
        boolean marcado = Boolean.TRUE.equals(request.porMayor());
        comprobante.setPorMayor(porCantidad || marcado);
        comprobante.setPorMayorManual(marcado && !porCantidad);
    }

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
            BigDecimal precio = comprobante.isPorMayor() ? variante.getPrecioPorMayorEfectivo() : variante.getPrecioEfectivo();
            BigDecimal descuento = item.descuento() == null ? BigDecimal.ZERO : item.descuento();
            if (descuento.compareTo(precio.multiply(BigDecimal.valueOf(item.cantidad()))) >= 0) {
                throw new BusinessException("El descuento de " + variante.getSku() + " no puede ser igual o mayor que su importe");
            }
            String afectacion = variante.getProducto().getTipoAfectacionIgv();
            CalculoIgv.Linea calculo = CalculoIgv.calcular(item.cantidad(), precio, descuento, afectacion);

            ComprobanteDetalle detalle = new ComprobanteDetalle();
            detalle.setVariante(variante);
            detalle.setSku(variante.getSku());
            detalle.setDescripcion(variante.getDescripcion());
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

    private Cliente clienteParaVenta(Long clienteId) {
        Cliente cliente = clienteId == null ? clienteService.obtenerGenericoEntidad() : clienteService.obtenerEntidad(clienteId);
        if (!cliente.isActivo()) {
            throw new BusinessException("El cliente " + cliente.getNombre() + " está inactivo");
        }
        return cliente;
    }

    private static List<String> numeros(List<Comprobante> notas, TipoComprobante tipo) {
        return notas.stream().filter(n -> n.getTipoComprobante() == tipo).map(Comprobante::getNumeroImpreso).toList();
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

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
