package com.allstore.api.compra.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.compra.dto.AnularCompraRequest;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.CompraResponse;
import com.allstore.api.compra.entity.Compra;
import com.allstore.api.compra.entity.CompraDetalle;
import com.allstore.api.compra.entity.Proveedor;
import com.allstore.api.compra.repository.CompraRepository;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.insumo.entity.Insumo;
import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.service.InsumoService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compras: registra el documento del proveedor, hace entrar la mercadería y los insumos al stock del
 * local y deja los importes listos para el Registro de Compras (RCE). El costo de las prendas es
 * manual: una compra lo actualiza solo si se pide.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    public static final String REFERENCIA_COMPRA = "COMPRA";
    static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    /** Lo que cabe en numeric(12,2): 10 enteros. */
    static final BigDecimal MAXIMO_IMPORTE = new BigDecimal("9999999999.99");

    private final CompraRepository compraRepository;
    private final ProveedorService proveedorService;
    private final EstablecimientoService establecimientoService;
    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final InsumoService insumoService;
    private final Bitacora bitacora;

    @Transactional
    public CompraResponse registrar(CompraRequest request) {
        Proveedor proveedor = proveedorService.obtener(request.proveedorId());
        Establecimiento local = establecimientoService.obtener(request.establecimientoId());
        String serie = request.serie().trim().toUpperCase();
        String numero = request.numero().trim();
        if (compraRepository.existsByProveedorIdAndTipoDocumentoAndSerieAndNumeroAndEstado(proveedor.getId(),
                request.tipoDocumento(), serie, numero, Compra.REGISTRADA)) {
            throw new BusinessException("La " + request.tipoDocumento().name().toLowerCase() + " " + serie + "-" + numero
                    + " de " + proveedor.getRazonSocial() + " ya está registrada");
        }
        if (request.fechaEmision().isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de emisión no puede ser futura");
        }
        YearMonth emision = YearMonth.from(request.fechaEmision());
        YearMonth periodo = request.periodo() != null ? YearMonth.parse(request.periodo()) : emision;
        if (periodo.isBefore(emision) || periodo.isAfter(emision.plusMonths(12))) {
            throw new BusinessException("El periodo del registro debe ser el de emisión o hasta 12 meses después");
        }

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setEstablecimiento(local);
        compra.setTipoDocumento(request.tipoDocumento());
        compra.setSerie(serie);
        compra.setNumero(numero);
        compra.setFechaEmision(request.fechaEmision());
        compra.setPeriodo(periodo.toString());
        compra.setObservacion(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);

        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(request.items().stream()
                .map(CompraRequest.Item::varianteId).filter(Objects::nonNull).toList());
        List<InventarioService.Linea> prendas = new ArrayList<>();
        List<InsumoService.Linea> insumos = new ArrayList<>();
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal igv = BigDecimal.ZERO;
        BigDecimal noGravado = BigDecimal.ZERO;
        for (CompraRequest.Item item : request.items()) {
            if (item.varianteId() != null && item.insumoId() != null) {
                throw new BusinessException("Una línea es una prenda o un insumo, no ambos");
            }
            CompraDetalle detalle = new CompraDetalle();
            if (item.varianteId() != null) {
                ProductoVariante variante = variantes.get(item.varianteId());
                if (item.cantidad().stripTrailingZeros().scale() > 0) {
                    throw new BusinessException("La cantidad de " + variante.getSku() + " debe ser un número entero de prendas");
                }
                detalle.setVariante(variante);
                detalle.setDescripcion(variante.getDescripcion());
                prendas.add(new InventarioService.Linea(variante.getId(), item.cantidad().intValueExact()));
                if (request.actualizarCostos()) {
                    variante.setCosto(item.costoUnitario().setScale(2, RoundingMode.HALF_UP));
                }
            } else if (item.insumoId() != null) {
                Insumo insumo = insumoService.obtener(item.insumoId());
                detalle.setInsumo(insumo);
                detalle.setDescripcion(insumo.getNombre());
                insumos.add(new InsumoService.Linea(insumo.getId(), item.cantidad()));
                if (request.actualizarCostos()) {
                    insumo.setCosto(item.costoUnitario());
                }
            } else {
                if (!StringUtils.hasText(item.descripcion())) {
                    throw new BusinessException("Una línea de servicio o gasto necesita su descripción");
                }
                detalle.setDescripcion(item.descripcion().trim());
            }
            boolean gravado = request.tipoDocumento().isCreditoFiscal() && (item.gravado() == null || item.gravado());
            BigDecimal valor = item.cantidad().multiply(item.costoUnitario()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal impuesto = gravado ? valor.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            detalle.setCantidad(item.cantidad());
            detalle.setCostoUnitario(item.costoUnitario());
            detalle.setGravado(gravado);
            detalle.setValor(valor);
            detalle.setIgv(impuesto);
            detalle.setTotal(valor.add(impuesto));
            compra.agregar(detalle);
            if (gravado) {
                base = base.add(valor);
                igv = igv.add(impuesto);
            } else {
                noGravado = noGravado.add(valor);
            }
        }
        compra.setBaseGravada(base);
        compra.setIgv(igv);
        compra.setNoGravado(noGravado);
        compra.setTotal(base.add(igv).add(noGravado));
        if (compra.getTotal().compareTo(MAXIMO_IMPORTE) > 0) {
            throw new BusinessException("El total de la compra supera el máximo permitido (S/ " + MAXIMO_IMPORTE.toPlainString() + ")");
        }
        if (compra.getTotal().signum() <= 0) {
            throw new BusinessException("El total de la compra debe ser mayor a cero");
        }
        compraRepository.save(compra);

        String documento = request.tipoDocumento().name().toLowerCase().replace('_', ' ') + " " + compra.getDocumento();
        if (!prendas.isEmpty()) {
            List<MovimientoInventario> movimientos = inventarioService.registrar(local.getId(), MotivoMovimiento.COMPRA, prendas,
                    proveedor.getRazonSocial());
            movimientos.forEach(m -> m.referenciar(REFERENCIA_COMPRA, compra.getId(), documento));
        }
        if (!insumos.isEmpty()) {
            insumoService.registrar(local.getId(), MotivoInsumo.COMPRA, insumos, REFERENCIA_COMPRA, compra.getId(), documento,
                    proveedor.getRazonSocial());
        }
        return CompraResponse.from(compra, true);
    }

    /** Anula la compra: la mercadería y los insumos vuelven a salir (si ya se vendieron o usaron, no se puede). */
    @Transactional
    public CompraResponse anular(Long id, AnularCompraRequest request) {
        Compra compra = obtener(id);
        if (Compra.ANULADA.equals(compra.getEstado())) {
            throw new BusinessException("La compra " + compra.getDocumento() + " ya está anulada");
        }
        String documento = "Anulación de compra " + compra.getDocumento();
        List<InventarioService.Linea> prendas = compra.getDetalles().stream().filter(d -> d.getVariante() != null)
                .map(d -> new InventarioService.Linea(d.getVariante().getId(), d.getCantidad().intValueExact())).toList();
        List<InsumoService.Linea> insumos = compra.getDetalles().stream().filter(d -> d.getInsumo() != null)
                .map(d -> new InsumoService.Linea(d.getInsumo().getId(), d.getCantidad())).toList();
        if (!prendas.isEmpty()) {
            inventarioService.registrar(compra.getEstablecimiento().getId(), MotivoMovimiento.ANULACION_COMPRA, prendas,
                    request.motivo().trim()).forEach(m -> m.referenciar(REFERENCIA_COMPRA, compra.getId(), documento));
        }
        if (!insumos.isEmpty()) {
            insumoService.registrar(compra.getEstablecimiento().getId(), MotivoInsumo.ANULACION_COMPRA, insumos,
                    REFERENCIA_COMPRA, compra.getId(), documento, request.motivo().trim());
        }
        compra.setEstado(Compra.ANULADA);
        compra.setMotivoAnulacion(request.motivo().trim());
        bitacora.registrar(AccionBitacora.COMPRA_ANULADA, "COMPRA", compra.getId(), "Anuló la compra " + compra.getDocumento()
                + " de " + compra.getProveedor().getRazonSocial() + " por S/ " + compra.getTotal(), "Motivo: " + request.motivo().trim());
        return CompraResponse.from(compra, true);
    }

    @Transactional(readOnly = true)
    public CompraResponse obtenerDetalle(Long id) {
        return CompraResponse.from(obtener(id), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompraResponse> listar(String periodo, Long proveedorId, Pageable pageable) {
        return PageResponse.of(compraRepository.buscar(periodo, proveedorId, pageable).map(c -> CompraResponse.from(c, false)));
    }

    private Compra obtener(Long id) {
        return compraRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compra", id));
    }
}
