package com.allstore.api.inventario.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.dto.MovimientoManualRequest;
import com.allstore.api.inventario.dto.MovimientoResponse;
import com.allstore.api.inventario.dto.StockEstablecimientoResponse;
import com.allstore.api.inventario.dto.StockResponse;
import com.allstore.api.inventario.dto.TrasladoRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.entity.Stock;
import com.allstore.api.inventario.entity.TipoMovimiento;
import com.allstore.api.inventario.repository.MovimientoInventarioRepository;
import com.allstore.api.inventario.repository.StockRepository;
import com.allstore.api.producto.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Único punto por el que cambia el stock. Ventas, traslados y ajustes llaman aquí, y cada
 * cambio deja su línea en el kardex con el saldo resultante.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    public static final String REFERENCIA_TRASLADO = "TRASLADO";

    private final StockRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final EstablecimientoService establecimientoService;
    private final ProductoService productoService;
    private final AuditorAware<String> usuarioActual;

    /** Una prenda y su cantidad, como la piden ventas y ajustes. */
    public record Linea(Long varianteId, int cantidad) {
    }

    private record Operacion(Long varianteId, Long establecimientoId, MotivoMovimiento motivo, int cantidad) {
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public PageResponse<StockResponse> stock(Long establecimientoId, String busqueda, boolean soloConStock,
                                             Pageable pageable) {
        String patron = "%" + (StringUtils.hasText(busqueda) ? busqueda.trim().toLowerCase() : "") + "%";
        return PageResponse.of(stockRepository.buscar(establecimientoId, patron, soloConStock, pageable)
                .map(StockResponse::from));
    }

    @Transactional(readOnly = true)
    public List<StockEstablecimientoResponse> stockPorVariante(Long varianteId) {
        productoService.obtenerVariantes(List.of(varianteId));
        return stockRepository.findByVariante(varianteId).stream().map(StockEstablecimientoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimientoResponse> kardex(Long varianteId, Long establecimientoId, LocalDate desde,
                                                   LocalDate hasta, Pageable pageable) {
        OffsetDateTime inicio = (desde != null ? desde : LocalDate.of(2000, 1, 1))
                .atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        OffsetDateTime fin = (hasta != null ? hasta : LocalDate.now(JpaAuditingConfig.ZONA_LIMA)).plusDays(1)
                .atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        if (!fin.isAfter(inicio)) {
            throw new BusinessException("La fecha 'desde' debe ser anterior o igual a 'hasta'");
        }
        return PageResponse.of(movimientoRepository.kardex(varianteId, establecimientoId, inicio, fin, pageable)
                .map(MovimientoResponse::from));
    }

    // ---------- Operaciones de usuarios ----------

    /** Inventario inicial o ajuste tras un conteo físico. */
    @Transactional
    public List<MovimientoResponse> registrarManual(MovimientoManualRequest request) {
        if (!request.motivo().isManual()) {
            throw new BusinessException("El motivo " + request.motivo() + " lo genera el sistema; para un ajuste usa "
                    + "INVENTARIO_INICIAL, AJUSTE_ENTRADA o AJUSTE_SALIDA");
        }
        List<MovimientoInventario> movimientos = registrar(request.establecimientoId(), request.motivo(),
                aLineas(request.items()), limpiar(request.observacion()));
        return movimientos.stream().map(MovimientoResponse::from).toList();
    }

    /** Salida del origen y entrada al destino en la misma transacción: o se mueven ambas o ninguna. */
    @Transactional
    public List<MovimientoResponse> trasladar(TrasladoRequest request) {
        if (request.origenId().equals(request.destinoId())) {
            throw new BusinessException("El origen y el destino del traslado deben ser distintos");
        }
        Establecimiento origen = establecimientoActivo(request.origenId());
        Establecimiento destino = establecimientoActivo(request.destinoId());
        List<Linea> lineas = aLineas(request.items());
        validarLineas(lineas);

        List<Operacion> operaciones = new ArrayList<>();
        for (Linea linea : lineas) {
            operaciones.add(new Operacion(linea.varianteId(), origen.getId(), MotivoMovimiento.TRASLADO_SALIDA, linea.cantidad()));
            operaciones.add(new Operacion(linea.varianteId(), destino.getId(), MotivoMovimiento.TRASLADO_ENTRADA, linea.cantidad()));
        }
        List<MovimientoInventario> movimientos = aplicar(operaciones, limpiar(request.observacion()));
        // Todos los movimientos del traslado comparten la misma referencia para poder agruparlos.
        Long grupo = movimientos.getFirst().getId();
        String documento = "Traslado " + origen.getNombre() + " -> " + destino.getNombre();
        movimientos.forEach(m -> m.referenciar(REFERENCIA_TRASLADO, grupo, documento));
        return movimientos.stream().map(MovimientoResponse::from).toList();
    }

    // ---------- Para otros módulos (ventas) ----------

    /**
     * Registra entradas o salidas de varias prendas en un establecimiento. Lo usan los ajustes y
     * las ventas (que se unen a esta transacción). Si alguna salida no tiene stock, falla todo.
     */
    @Transactional
    public List<MovimientoInventario> registrar(Long establecimientoId, MotivoMovimiento motivo, List<Linea> lineas,
                                                String observacion) {
        Establecimiento establecimiento = establecimientoActivo(establecimientoId);
        validarLineas(lineas);
        List<Operacion> operaciones = lineas.stream()
                .map(l -> new Operacion(l.varianteId(), establecimiento.getId(), motivo, l.cantidad()))
                .toList();
        return aplicar(operaciones, observacion);
    }

    /**
     * Aplica las operaciones bloqueando cada fila de stock. Se ordenan por variante y
     * establecimiento para que todas las transacciones bloqueen en el mismo orden: si dos ventas
     * llevan las mismas prendas, una espera a la otra en vez de bloquearse mutuamente (deadlock).
     */
    private List<MovimientoInventario> aplicar(List<Operacion> operaciones, String observacion) {
        String usuario = usuarioActual.getCurrentAuditor().orElse(JpaAuditingConfig.USUARIO_SISTEMA);
        List<Operacion> ordenadas = operaciones.stream()
                .sorted(Comparator.comparing(Operacion::varianteId).thenComparing(Operacion::establecimientoId))
                .toList();
        List<MovimientoInventario> movimientos = new ArrayList<>();
        for (Operacion op : ordenadas) {
            stockRepository.crearSiNoExiste(op.varianteId(), op.establecimientoId(), usuario);
            Stock stock = stockRepository.bloquear(op.varianteId(), op.establecimientoId())
                    .orElseThrow(() -> new IllegalStateException("No se pudo crear el stock de la variante " + op.varianteId()));
            if (op.motivo().getTipo() == TipoMovimiento.ENTRADA) {
                stock.sumar(op.cantidad());
            } else {
                stock.restar(op.cantidad());
            }
            movimientos.add(movimientoRepository.save(new MovimientoInventario(stock, op.motivo(), op.cantidad(), observacion)));
        }
        return movimientos;
    }

    /** Las variantes deben existir y no repetirse (la misma prenda dos veces se suma en una línea). */
    private void validarLineas(List<Linea> lineas) {
        Set<Long> vistas = new HashSet<>();
        for (Linea linea : lineas) {
            if (!vistas.add(linea.varianteId())) {
                throw new BusinessException("La variante " + linea.varianteId()
                        + " aparece más de una vez; suma las cantidades en una sola línea");
            }
            if (linea.cantidad() <= 0) {
                throw new BusinessException("La cantidad debe ser mayor a cero");
            }
        }
        productoService.obtenerVariantes(vistas);
    }

    private Establecimiento establecimientoActivo(Long id) {
        Establecimiento establecimiento = establecimientoService.obtener(id);
        if (!establecimiento.isActivo()) {
            throw new BusinessException("El establecimiento " + establecimiento.getNombre() + " está inactivo");
        }
        return establecimiento;
    }

    private static List<Linea> aLineas(List<ItemCantidadRequest> items) {
        return items.stream().map(i -> new Linea(i.varianteId(), i.cantidad())).toList();
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
