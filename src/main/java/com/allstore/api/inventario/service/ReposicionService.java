package com.allstore.api.inventario.service;

import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Nivel;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.FuenteAlertas;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.inventario.dto.ReposicionFila;
import com.allstore.api.inventario.dto.StockMinimoRequest;
import com.allstore.api.inventario.entity.StockMinimo;
import com.allstore.api.inventario.repository.StockMinimoRepository;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.reporte.exportacion.Columna;
import com.allstore.api.reporte.exportacion.Reporte;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qué reponer: las prendas que llegaron a su stock mínimo (o se agotaron) en cada local, con lo que
 * rotan y lo que hay en otros locales para trasladar antes de comprar.
 *
 * <p>El mínimo del modelo rige en las tiendas; una prenda puede tener su propio mínimo en cualquier
 * local (también el almacén), que manda sobre el del modelo.
 */
@Service
@RequiredArgsConstructor
public class ReposicionService implements FuenteAlertas {

    private final StockMinimoRepository stockMinimoRepository;
    private final ProductoService productoService;
    private final EstablecimientoService establecimientoService;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public List<ReposicionFila> definirMinimos(StockMinimoRequest request) {
        Establecimiento local = establecimientoService.obtener(request.establecimientoId());
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(
                request.items().stream().map(StockMinimoRequest.Item::varianteId).toList());
        for (StockMinimoRequest.Item item : request.items()) {
            var existente = stockMinimoRepository.findByVarianteIdAndEstablecimientoId(item.varianteId(), local.getId());
            if (item.minimo() == null) {
                existente.ifPresent(stockMinimoRepository::delete);
                continue;
            }
            StockMinimo minimo = existente.orElseGet(() -> {
                StockMinimo nuevo = new StockMinimo();
                nuevo.setVariante(variantes.get(item.varianteId()));
                nuevo.setEstablecimiento(local);
                return nuevo;
            });
            minimo.setMinimo(item.minimo());
            stockMinimoRepository.save(minimo);
        }
        stockMinimoRepository.flush();
        return pendientes(local.getId());
    }

    @Transactional(readOnly = true)
    public Reporte<ReposicionFila> reporte(Long establecimientoId) {
        return new Reporte<>("reposicion", "Reposición", null, null, pendientes(establecimientoId), List.of(
                Columna.texto("Local", ReposicionFila::establecimiento),
                Columna.texto("SKU", ReposicionFila::sku),
                Columna.texto("Producto", ReposicionFila::producto),
                Columna.texto("Talla", ReposicionFila::talla),
                Columna.texto("Color", ReposicionFila::color),
                Columna.entero("Stock", ReposicionFila::stock),
                Columna.entero("Mínimo", ReposicionFila::minimo),
                Columna.entero("Faltan", ReposicionFila::faltan),
                Columna.entero("Vendidas 30 días", ReposicionFila::vendidas30Dias),
                Columna.entero("En otros locales", ReposicionFila::enOtrosLocales)));
    }

    /** Una alerta por local con prendas en o bajo su mínimo (no una por prenda: sería ruido). */
    @Override
    @Transactional(readOnly = true)
    public List<Alerta> alertas() {
        Map<String, List<ReposicionFila>> porLocal = new LinkedHashMap<>();
        pendientes(null).forEach(f -> porLocal.computeIfAbsent(f.establecimiento(), k -> new ArrayList<>()).add(f));
        List<Alerta> alertas = new ArrayList<>();
        porLocal.forEach((local, filas) -> {
            long agotadas = filas.stream().filter(ReposicionFila::agotada).count();
            alertas.add(Alerta.general(Nivel.ADVERTENCIA, Tipo.STOCK_BAJO, local + ": " + filas.size()
                    + (filas.size() == 1 ? " prenda está" : " prendas están") + " en o bajo su stock mínimo"
                    + (agotadas > 0 ? " (" + agotadas + (agotadas == 1 ? " agotada" : " agotadas") + ")" : "")
                    + ". Revisa la lista de reposición: GET /api/inventario/reposicion"));
        });
        return alertas;
    }

    /** Prendas activas con stock en o bajo su mínimo; primero las agotadas y las que más faltan. */
    List<ReposicionFila> pendientes(Long establecimientoId) {
        return jdbc.query("""
                with minimos as (
                    select v.id variante_id, e.id establecimiento_id, sm.minimo is not null propio,
                           coalesce(sm.minimo, case when e.tipo = 'TIENDA' then p.stock_minimo end) minimo
                    from producto_variante v
                    join producto p on p.id = v.producto_id
                    cross join establecimiento e
                    left join stock_minimo sm on sm.variante_id = v.id and sm.establecimiento_id = e.id
                    where v.activo and p.activo and e.activo
                      and (cast(:est as bigint) is null or e.id = :est))
                select v.id, v.sku, p.nombre, t.codigo, co.nombre, e.id, e.nombre,
                       coalesce(s.cantidad, 0) stock, m.minimo, m.propio,
                       greatest(m.minimo - coalesce(s.cantidad, 0), 0) faltan,
                       coalesce((select sum(d.cantidad) from comprobante_detalle d join comprobante c on c.id = d.comprobante_id
                                 where d.variante_id = v.id and c.establecimiento_id = e.id
                                   and c.tipo_comprobante in ('NV', '03', '01') and c.estado in ('EMITIDO', 'CONVERTIDO')
                                   and c.comprobante_origen_id is null
                                   and c.fecha_emision >= now() - interval '30 days'), 0) vendidas,
                       coalesce((select sum(o.cantidad) from stock o
                                 where o.variante_id = v.id and o.establecimiento_id <> e.id), 0) otros
                from minimos m
                join producto_variante v on v.id = m.variante_id
                join producto p on p.id = v.producto_id
                join talla t on t.id = v.talla_id
                join color co on co.id = v.color_id
                join establecimiento e on e.id = m.establecimiento_id
                left join stock s on s.variante_id = v.id and s.establecimiento_id = e.id
                where m.minimo > 0 and coalesce(s.cantidad, 0) <= m.minimo
                order by e.nombre, (coalesce(s.cantidad, 0) = 0) desc, faltan desc, p.nombre, t.orden, co.nombre
                """, new MapSqlParameterSource("est", establecimientoId), (rs, i) -> new ReposicionFila(rs.getLong(1),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7),
                rs.getLong(8), rs.getInt(9), rs.getBoolean(10), rs.getLong(11), rs.getLong(12), rs.getLong(13)));
    }
}
