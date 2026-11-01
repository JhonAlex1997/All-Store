package com.allstore.api.insumo.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.insumo.dto.AjusteInsumoRequest;
import com.allstore.api.insumo.dto.InsumoRequest;
import com.allstore.api.insumo.dto.InsumoResponse;
import com.allstore.api.insumo.dto.MovimientoInsumoResponse;
import com.allstore.api.insumo.dto.StockInsumoResponse;
import com.allstore.api.insumo.entity.Insumo;
import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.entity.MovimientoInsumo;
import com.allstore.api.insumo.entity.StockInsumo;
import com.allstore.api.insumo.repository.InsumoRepository;
import com.allstore.api.insumo.repository.MovimientoInsumoRepository;
import com.allstore.api.insumo.repository.StockInsumoRepository;
import com.allstore.api.seguridad.service.UsuarioEnSesion;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Catálogo de insumos, su stock por local y su kardex. Compras y producción mueven el stock por aquí. */
@Service
@RequiredArgsConstructor
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final StockInsumoRepository stockRepository;
    private final MovimientoInsumoRepository movimientoRepository;
    private final EstablecimientoService establecimientoService;
    private final UsuarioEnSesion usuarioEnSesion;
    private final Bitacora bitacora;

    /** Una cantidad de un insumo para mover. */
    public record Linea(Long insumoId, BigDecimal cantidad) {
    }

    @Transactional(readOnly = true)
    public List<InsumoResponse> listar() {
        return insumoRepository.findAllByOrderByNombreAsc().stream().map(InsumoResponse::from).toList();
    }

    @Transactional
    public InsumoResponse crear(InsumoRequest request) {
        String codigo = request.codigo().trim().toUpperCase();
        if (insumoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new BusinessException("Ya existe el insumo " + codigo);
        }
        Insumo insumo = new Insumo();
        insumo.setCodigo(codigo);
        aplicar(insumo, request);
        return InsumoResponse.from(insumoRepository.save(insumo));
    }

    @Transactional
    public InsumoResponse actualizar(Long id, InsumoRequest request) {
        Insumo insumo = obtener(id);
        aplicar(insumo, request);
        return InsumoResponse.from(insumo);
    }

    @Transactional(readOnly = true)
    public List<StockInsumoResponse> stock(Long establecimientoId) {
        return stockRepository.listar(establecimientoId).stream().map(StockInsumoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimientoInsumoResponse> kardex(Long insumoId, Long establecimientoId, Pageable pageable) {
        obtener(insumoId);
        return PageResponse.of(movimientoRepository.kardex(insumoId, establecimientoId, pageable).map(MovimientoInsumoResponse::from));
    }

    @Transactional
    public List<MovimientoInsumoResponse> ajustar(AjusteInsumoRequest request) {
        if (!request.motivo().isManual()) {
            throw new BusinessException("El motivo " + request.motivo() + " lo genera el sistema; para un ajuste usa "
                    + "INVENTARIO_INICIAL, AJUSTE_ENTRADA o AJUSTE_SALIDA");
        }
        List<Linea> lineas = request.items().stream().map(i -> new Linea(i.insumoId(), i.cantidad())).toList();
        String observacion = StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null;
        List<MovimientoInsumo> movimientos = registrar(request.establecimientoId(), request.motivo(), lineas, null, null, "Ajuste",
                observacion);
        bitacora.registrar(AccionBitacora.AJUSTE_INSUMO, "INSUMO", request.establecimientoId(), request.motivo() + " de insumos",
                String.join(", ", movimientos.stream().map(m -> m.getInsumo().getCodigo() + " " + (m.getMotivo().isEntrada() ? "+" : "-")
                        + m.getCantidad().stripTrailingZeros().toPlainString()).toList()) + (observacion != null ? ". " + observacion : ""));
        return movimientos.stream().map(MovimientoInsumoResponse::from).toList();
    }

    /**
     * Entradas o salidas de insumos en un local, en la transacción de quien llama (compras,
     * producción). Bloquea los saldos en orden fijo (evita deadlocks); si alguna salida no alcanza, falla todo.
     */
    @Transactional
    public List<MovimientoInsumo> registrar(Long establecimientoId, MotivoInsumo motivo, List<Linea> lineas,
                                            String referenciaTipo, Long referenciaId, String documento, String observacion) {
        Establecimiento local = establecimientoService.obtener(establecimientoId);
        Set<Long> vistos = new HashSet<>();
        for (Linea l : lineas) {
            if (!vistos.add(l.insumoId())) {
                throw new BusinessException("Un insumo aparece más de una vez; suma las cantidades en una sola línea");
            }
            if (l.cantidad() == null || l.cantidad().signum() <= 0) {
                throw new BusinessException("La cantidad debe ser mayor a cero");
            }
        }
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        String usuario = usuarioEnSesion.nombre();
        List<MovimientoInsumo> movimientos = new ArrayList<>();
        for (Linea l : lineas.stream().sorted(Comparator.comparing(Linea::insumoId)).toList()) {
            StockInsumo stock = stockRepository.bloquear(l.insumoId(), local.getId()).orElseGet(() -> {
                StockInsumo nuevo = new StockInsumo(obtener(l.insumoId()), local);
                return stockRepository.saveAndFlush(nuevo);
            });
            if (motivo.isEntrada()) {
                stock.sumar(l.cantidad());
            } else {
                stock.restar(l.cantidad());
            }
            movimientos.add(movimientoRepository.save(new MovimientoInsumo(stock, motivo, l.cantidad(), referenciaTipo,
                    referenciaId, documento, observacion, ahora, usuario)));
        }
        return movimientos;
    }

    public Insumo obtener(Long id) {
        return insumoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Insumo", id));
    }

    private static void aplicar(Insumo insumo, InsumoRequest r) {
        insumo.setNombre(r.nombre().trim());
        insumo.setUnidadMedida(r.unidadMedida());
        insumo.setCosto(r.costo());
        insumo.setActivo(r.activo() == null || r.activo());
    }
}
