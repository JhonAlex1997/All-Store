package com.allstore.api.inventario.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.inventario.dto.CerrarConteoRequest;
import com.allstore.api.inventario.dto.ConteoCantidadesRequest;
import com.allstore.api.inventario.dto.ConteoRequest;
import com.allstore.api.inventario.dto.ConteoResponse;
import com.allstore.api.inventario.entity.ConteoDetalle;
import com.allstore.api.inventario.entity.ConteoInventario;
import com.allstore.api.inventario.entity.EstadoConteo;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.repository.ConteoInventarioRepository;
import com.allstore.api.inventario.repository.StockRepository;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.reporte.exportacion.Columna;
import com.allstore.api.reporte.exportacion.Reporte;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conteo físico: se abre para un local (todo o algunos modelos), se registra lo contado (a mano o
 * escaneando) y al cerrar las diferencias quedan ajustadas en el kardex con su referencia al conteo.
 *
 * <p>Se puede contar con la tienda abierta: cada prenda guarda el stock del sistema en el momento en
 * que se contó y la diferencia se mide contra ese, así lo vendido después no aparece como faltante.
 */
@Service
@RequiredArgsConstructor
public class ConteoService {

    public static final String REFERENCIA_CONTEO = "CONTEO";

    private final ConteoInventarioRepository conteoRepository;
    private final StockRepository stockRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final ProductoService productoService;
    private final EstablecimientoService establecimientoService;
    private final InventarioService inventarioService;
    private final Bitacora bitacora;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public ConteoResponse abrir(ConteoRequest request) {
        Establecimiento local = establecimientoService.obtener(request.establecimientoId());
        if (!local.isActivo()) {
            throw new BusinessException("El establecimiento " + local.getNombre() + " está inactivo");
        }
        if (conteoRepository.existsByEstablecimientoIdAndEstado(local.getId(), EstadoConteo.ABIERTO)) {
            throw new BusinessException("Ya hay un conteo abierto en " + local.getNombre() + ": ciérralo o anúlalo antes de abrir otro");
        }
        boolean filtrado = request.categoriaId() != null || (request.productoIds() != null && !request.productoIds().isEmpty());
        Map<Long, Integer> enAlcance = alcance(local.getId(), request.categoriaId(), request.productoIds(), filtrado);
        if (enAlcance.isEmpty()) {
            throw new BusinessException("No hay prendas que contar con esos filtros en " + local.getNombre());
        }
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(enAlcance.keySet());

        ConteoInventario conteo = new ConteoInventario();
        conteo.setEstablecimiento(local);
        conteo.setAlcance(descripcionAlcance(request, variantes));
        conteo.setObservacion(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);
        enAlcance.forEach((varianteId, stock) -> {
            ConteoDetalle detalle = new ConteoDetalle();
            detalle.setVariante(variantes.get(varianteId));
            detalle.setStockInicial(stock);
            conteo.agregar(detalle);
        });
        return respuesta(conteoRepository.save(conteo));
    }

    /** Registra lo contado. Una prenda que no estaba en el conteo (ej. otra categoría) se agrega. */
    @Transactional
    public ConteoResponse registrar(Long id, ConteoCantidadesRequest request) {
        ConteoInventario conteo = bloquearAbierto(id);
        boolean sumar = request.modo() == ConteoCantidadesRequest.Modo.SUMAR;
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        Map<Long, ConteoDetalle> porVariante = new LinkedHashMap<>();
        conteo.getDetalles().forEach(d -> porVariante.put(d.getVariante().getId(), d));
        for (ConteoCantidadesRequest.Item item : request.items()) {
            ProductoVariante variante = variante(item);
            int actual = stockActual(variante.getId(), conteo.getEstablecimiento().getId());
            ConteoDetalle detalle = porVariante.computeIfAbsent(variante.getId(), v -> {
                ConteoDetalle nuevo = new ConteoDetalle();
                nuevo.setVariante(variante);
                nuevo.setStockInicial(actual);
                conteo.agregar(nuevo);
                return nuevo;
            });
            if (sumar && detalle.getContado() != null) {
                detalle.setContado(detalle.getContado() + item.cantidad());
            } else {
                // Primera vez (o reemplazo): la diferencia se medirá contra el stock de este momento.
                detalle.setContado(item.cantidad());
                detalle.setStockAlContar(actual);
            }
            detalle.setFechaConteo(ahora);
        }
        return respuesta(conteo);
    }

    /**
     * Ajusta las diferencias y cierra. Cerrar uno ya cerrado devuelve su resultado (un reintento no
     * ajusta dos veces).
     */
    @Transactional
    public ConteoResponse cerrar(Long id, CerrarConteoRequest request) {
        ConteoInventario conteo = conteoRepository.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Conteo", id));
        if (conteo.getEstado() == EstadoConteo.CERRADO) {
            return respuesta(conteo);
        }
        exigirAbierto(conteo);
        Long local = conteo.getEstablecimiento().getId();
        boolean noContadosEnCero = request != null && request.noContadosEnCero();
        if (!noContadosEnCero && conteo.getDetalles().stream().allMatch(d -> d.getContado() == null)) {
            throw new BusinessException("No se contó ninguna prenda: registra lo contado o anula el conteo");
        }

        List<InventarioService.Linea> sobrantes = new ArrayList<>();
        List<InventarioService.Linea> faltantes = new ArrayList<>();
        Map<Long, ConteoDetalle> porVariante = new LinkedHashMap<>();
        for (ConteoDetalle d : conteo.getDetalles()) {
            int actual = stockActual(d.getVariante().getId(), local);
            if (d.getContado() == null) {
                if (!noContadosEnCero) {
                    continue;
                }
                d.setContado(0);
                d.setStockAlContar(actual);
            }
            int diferencia = d.diferencia();
            if (diferencia > 0) {
                sobrantes.add(new InventarioService.Linea(d.getVariante().getId(), diferencia));
                d.setAjuste(diferencia);
            } else if (diferencia < 0) {
                // Si después de contar se vendió más de lo que había (error de conteo), no se resta bajo cero.
                int restar = Math.min(-diferencia, actual);
                if (restar > 0) {
                    faltantes.add(new InventarioService.Linea(d.getVariante().getId(), restar));
                }
                d.setAjuste(-restar);
            }
            porVariante.put(d.getVariante().getId(), d);
        }
        String documento = "Conteo físico N° " + conteo.getId();
        List<MovimientoInventario> movimientos = new ArrayList<>();
        if (!sobrantes.isEmpty()) {
            movimientos.addAll(inventarioService.registrar(local, MotivoMovimiento.AJUSTE_ENTRADA, sobrantes, documento));
        }
        if (!faltantes.isEmpty()) {
            movimientos.addAll(inventarioService.registrar(local, MotivoMovimiento.AJUSTE_SALIDA, faltantes, documento));
        }
        movimientos.forEach(m -> m.referenciar(REFERENCIA_CONTEO, conteo.getId(), documento));

        int unidadesSobrantes = sobrantes.stream().mapToInt(InventarioService.Linea::cantidad).sum();
        int unidadesFaltantes = faltantes.stream().mapToInt(InventarioService.Linea::cantidad).sum();
        conteo.setUnidadesSobrantes(unidadesSobrantes);
        conteo.setUnidadesFaltantes(unidadesFaltantes);
        conteo.setEstado(EstadoConteo.CERRADO);
        conteo.setFechaCierre(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        ConteoResponse respuesta = respuesta(conteo);
        bitacora.registrar(AccionBitacora.CONTEO_CERRADO, "CONTEO", conteo.getId(), "Cerró el " + documento.toLowerCase()
                        + " de " + conteo.getEstablecimiento().getNombre() + ": +" + unidadesSobrantes + " sobrantes, -"
                        + unidadesFaltantes + " faltantes (S/ " + respuesta.valorDiferencia() + " a precio de venta)",
                conteo.getAlcance() + (noContadosEnCero ? ". Lo no contado se ajustó a 0" : ". Lo no contado no se tocó"));
        return respuesta;
    }

    @Transactional
    public ConteoResponse anular(Long id) {
        ConteoInventario conteo = bloquearAbierto(id);
        conteo.setEstado(EstadoConteo.ANULADO);
        conteo.setFechaCierre(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        return respuesta(conteo);
    }

    @Transactional(readOnly = true)
    public ConteoResponse obtener(Long id) {
        return respuesta(conteoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conteo", id)));
    }

    /** Lista sin las líneas (resumen de cada conteo). */
    @Transactional(readOnly = true)
    public PageResponse<ConteoResponse> listar(Long establecimientoId, EstadoConteo estado, Pageable pageable) {
        return PageResponse.of(conteoRepository.buscar(establecimientoId, estado, pageable).map(c -> sinLineas(respuesta(c))));
    }

    /** Hoja de conteo para imprimir o llenar en Excel: prendas, stock del sistema y lo contado. */
    @Transactional(readOnly = true)
    public Reporte<ConteoResponse.Linea> hoja(Long id) {
        ConteoResponse conteo = obtener(id);
        return new Reporte<>("conteo-" + id, "Conteo " + id + " " + conteo.establecimiento(), null, null, conteo.lineas(), List.of(
                Columna.texto("SKU", ConteoResponse.Linea::sku),
                Columna.texto("Producto", ConteoResponse.Linea::producto),
                Columna.texto("Talla", ConteoResponse.Linea::talla),
                Columna.texto("Color", ConteoResponse.Linea::color),
                Columna.entero("Stock sistema", ConteoResponse.Linea::stockInicial),
                Columna.entero("Contado", ConteoResponse.Linea::contado),
                Columna.entero("Diferencia", ConteoResponse.Linea::diferencia),
                Columna.entero("Ajuste", ConteoResponse.Linea::ajuste)));
    }

    // ---------- Apoyo ----------

    /** Prendas a contar y su stock actual: las que el local ha tenido o, con filtros, todas las de esos modelos. */
    private Map<Long, Integer> alcance(Long local, Long categoriaId, List<Long> productoIds, boolean filtrado) {
        MapSqlParameterSource params = new MapSqlParameterSource("local", local)
                .addValue("categoria", categoriaId)
                .addValue("productos", productoIds == null || productoIds.isEmpty() ? List.of(-1L) : productoIds)
                .addValue("conProductos", productoIds != null && !productoIds.isEmpty());
        String sql = """
                select v.id, coalesce(s.cantidad, 0)
                from producto_variante v
                join producto p on p.id = v.producto_id
                join talla t on t.id = v.talla_id
                left join stock s on s.variante_id = v.id and s.establecimiento_id = :local
                where v.activo and p.activo
                  and (cast(:categoria as bigint) is null or p.categoria_id = :categoria)
                  and (not :conProductos or p.id in (:productos))
                """ + (filtrado ? "" : " and s.id is not null") + " order by p.nombre, t.orden, v.sku";
        Map<Long, Integer> resultado = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            resultado.put(rs.getLong(1), rs.getInt(2));
        });
        return resultado;
    }

    private static String descripcionAlcance(ConteoRequest request, Map<Long, ProductoVariante> variantes) {
        if (request.productoIds() != null && !request.productoIds().isEmpty()) {
            return "Modelos: " + String.join(", ", variantes.values().stream().map(v -> v.getProducto().getCodigo()).distinct().sorted().toList());
        }
        if (request.categoriaId() != null) {
            return "Categoría " + variantes.values().iterator().next().getProducto().getCategoria().getNombre();
        }
        return "Todo el local";
    }

    private ProductoVariante variante(ConteoCantidadesRequest.Item item) {
        if (item.varianteId() != null) {
            return productoService.obtenerVariantes(List.of(item.varianteId())).get(item.varianteId());
        }
        if (!StringUtils.hasText(item.codigo())) {
            throw new BusinessException("Cada prenda contada necesita su varianteId o su código");
        }
        return varianteRepository.findByCodigoBarrasOrSku(item.codigo().trim())
                .orElseThrow(() -> new BusinessException("No existe una prenda con el código " + item.codigo().trim()));
    }

    private int stockActual(Long varianteId, Long local) {
        return stockRepository.cantidad(varianteId, local).orElse(0);
    }

    private ConteoInventario bloquearAbierto(Long id) {
        ConteoInventario conteo = conteoRepository.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Conteo", id));
        exigirAbierto(conteo);
        return conteo;
    }

    private static void exigirAbierto(ConteoInventario conteo) {
        if (!conteo.isAbierto()) {
            throw new BusinessException("El conteo " + conteo.getId() + " ya está " + conteo.getEstado().name().toLowerCase());
        }
    }

    private static ConteoResponse respuesta(ConteoInventario c) {
        List<ConteoResponse.Linea> lineas = c.getDetalles().stream()
                .sorted(Comparator.comparing((ConteoDetalle d) -> d.getVariante().getProducto().getNombre())
                        .thenComparing(d -> d.getVariante().getTalla().getOrden())
                        .thenComparing(d -> d.getVariante().getSku()))
                .map(d -> new ConteoResponse.Linea(d.getVariante().getId(), d.getVariante().getSku(),
                        d.getVariante().getProducto().getNombre(), d.getVariante().getTalla().getCodigo(),
                        d.getVariante().getColor().getNombre(), d.getVariante().getPrecioEfectivo(), d.getStockInicial(),
                        d.getContado(), d.getStockAlContar(), d.diferencia(), d.getAjuste()))
                .toList();
        boolean cerrado = c.getEstado() == EstadoConteo.CERRADO;
        int sobrantes = cerrado ? Optional.ofNullable(c.getUnidadesSobrantes()).orElse(0)
                : lineas.stream().filter(l -> l.diferencia() != null && l.diferencia() > 0).mapToInt(ConteoResponse.Linea::diferencia).sum();
        int faltantes = cerrado ? Optional.ofNullable(c.getUnidadesFaltantes()).orElse(0)
                : -lineas.stream().filter(l -> l.diferencia() != null && l.diferencia() < 0).mapToInt(ConteoResponse.Linea::diferencia).sum();
        BigDecimal valor = lineas.stream()
                .map(l -> {
                    Integer cambio = cerrado ? l.ajuste() : l.diferencia();
                    return cambio == null ? BigDecimal.ZERO : l.precio().multiply(BigDecimal.valueOf(cambio));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        int contadas = (int) lineas.stream().filter(l -> l.contado() != null).count();
        return new ConteoResponse(c.getId(), c.getEstablecimiento().getId(), c.getEstablecimiento().getNombre(), c.getEstado(),
                c.getAlcance(), c.getObservacion(), c.getCreatedBy(), c.getCreatedAt(), c.getFechaCierre(), lineas.size(),
                contadas, lineas.size() - contadas, sobrantes, faltantes, valor, lineas);
    }

    private static ConteoResponse sinLineas(ConteoResponse r) {
        return new ConteoResponse(r.id(), r.establecimientoId(), r.establecimiento(), r.estado(), r.alcance(), r.observacion(),
                r.abiertoPor(), r.fechaInicio(), r.fechaCierre(), r.prendas(), r.contadas(), r.porContar(), r.sobrantes(),
                r.faltantes(), r.valorDiferencia(), List.of());
    }
}
