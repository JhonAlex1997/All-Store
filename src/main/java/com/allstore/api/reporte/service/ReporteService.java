package com.allstore.api.reporte.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.reporte.dto.DetalleVentaFila;
import com.allstore.api.reporte.dto.SinRotacionFila;
import com.allstore.api.reporte.dto.VentaDiaFila;
import com.allstore.api.reporte.dto.VentaDiaSemanaFila;
import com.allstore.api.reporte.dto.VentaHoraFila;
import com.allstore.api.reporte.dto.VentaProductoFila;
import com.allstore.api.reporte.dto.VentaTallaColorFila;
import com.allstore.api.reporte.dto.VentaVendedorFila;
import com.allstore.api.reporte.exportacion.Columna;
import com.allstore.api.reporte.exportacion.Reporte;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reportes de ventas e inventario para decidir: qué se vende (modelo, talla, color), quién vende,
 * cuándo, y qué no rota. Todos cuentan solo lo vigente, igual que los totales de ventas: ni anulados,
 * ni notas de venta ya convertidas (cuenta su boleta o factura), ni rechazados ya reemitidos; las
 * devoluciones con nota de crédito restan.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    /** Un año como máximo por consulta: protege la memoria del servidor y la base. */
    static final int MAXIMO_DIAS = 366;
    private static final String VENTAS = "('NV', '03', '01')";
    private static final String[] DIAS = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

    /**
     * Quién hizo la venta: la boleta de una nota de venta convertida, o la reemisión de un
     * rechazado, la crea otro usuario (o el mismo, otro día) pero la venta fue del original.
     */
    private static final String VENDEDOR = "coalesce(o.created_by, r.created_by, c.created_by)";
    private static final String UNIONES_VENDEDOR = """
            left join comprobante o on o.id = c.comprobante_origen_id and c.tipo_comprobante in ('NV', '03', '01')
            left join comprobante r on r.id = c.reemplaza_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public record Periodo(LocalDate desde, LocalDate hasta, Long establecimientoId) {
    }

    /** Sin fechas: el mes en curso hasta hoy. */
    public Periodo periodo(LocalDate desde, LocalDate hasta, Long establecimientoId) {
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate inicio = desde != null ? desde : (hasta != null ? fin.withDayOfMonth(1) : hoy.withDayOfMonth(1));
        if (inicio.isAfter(fin)) {
            throw new BusinessException("La fecha desde (" + inicio + ") no puede ser posterior a hasta (" + fin + ")");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) >= MAXIMO_DIAS) {
            throw new BusinessException("El periodo puede ser de hasta " + MAXIMO_DIAS + " días; pide el resto por partes");
        }
        return new Periodo(inicio, fin, establecimientoId);
    }

    public Reporte<VentaProductoFila> porProducto(Periodo p) {
        List<VentaProductoFila> filas = jdbc.query("""
                select pr.id, pr.codigo, pr.nombre, ca.nombre categoria,
                       sum(%s * d.cantidad) unidades,
                       count(distinct c.id) filter (where c.tipo_comprobante <> '07') comprobantes,
                       sum(%s * d.total) total
                %s
                join producto_variante v on v.id = d.variante_id
                join producto pr on pr.id = v.producto_id
                join categoria ca on ca.id = pr.categoria_id
                %s
                group by pr.id, pr.codigo, pr.nombre, ca.nombre
                order by total desc, unidades desc
                """.formatted(SIGNO, SIGNO, LINEAS, LINEAS_VIGENTES), parametros(p), (rs, i) -> new VentaProductoFila(rs.getLong(1),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getLong(6), dinero(rs, 7), null));
        BigDecimal total = filas.stream().map(VentaProductoFila::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        filas = filas.stream().map(f -> new VentaProductoFila(f.productoId(), f.codigo(), f.producto(), f.categoria(),
                f.unidades(), f.comprobantes(), f.total(), porcentaje(f.total(), total))).toList();
        return new Reporte<>("ventas-por-producto", "Ventas por producto", p.desde(), p.hasta(), filas, List.of(
                Columna.texto("Código", VentaProductoFila::codigo),
                Columna.texto("Producto", VentaProductoFila::producto),
                Columna.texto("Categoría", VentaProductoFila::categoria),
                Columna.entero("Unidades", VentaProductoFila::unidades),
                Columna.entero("Comprobantes", VentaProductoFila::comprobantes),
                Columna.moneda("Total (S/)", VentaProductoFila::total),
                Columna.porcentaje("% del total", VentaProductoFila::participacion)));
    }

    public Reporte<VentaTallaColorFila> porTallaColor(Periodo p) {
        MapSqlParameterSource params = parametros(p);
        List<VentaTallaColorFila> filas = jdbc.query("""
                select v.id, v.sku, pr.nombre, t.codigo talla, co.nombre color,
                       sum(%s * d.cantidad) unidades, sum(%s * d.total) total,
                       coalesce((select sum(s.cantidad) from stock s where s.variante_id = v.id
                                 and (cast(:est as bigint) is null or s.establecimiento_id = :est)), 0) stock
                %s
                join producto_variante v on v.id = d.variante_id
                join producto pr on pr.id = v.producto_id
                join talla t on t.id = v.talla_id
                join color co on co.id = v.color_id
                %s
                group by v.id, v.sku, pr.nombre, t.codigo, t.orden, co.nombre
                order by unidades desc, total desc
                """.formatted(SIGNO, SIGNO, LINEAS, LINEAS_VIGENTES), params, (rs, i) -> new VentaTallaColorFila(rs.getLong(1),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), dinero(rs, 7), rs.getLong(8)));
        return new Reporte<>("ventas-por-talla-color", "Ventas por talla y color", p.desde(), p.hasta(), filas, List.of(
                Columna.texto("SKU", VentaTallaColorFila::sku),
                Columna.texto("Producto", VentaTallaColorFila::producto),
                Columna.texto("Talla", VentaTallaColorFila::talla),
                Columna.texto("Color", VentaTallaColorFila::color),
                Columna.entero("Unidades", VentaTallaColorFila::unidades),
                Columna.moneda("Total (S/)", VentaTallaColorFila::total),
                Columna.entero("Stock actual", VentaTallaColorFila::stockActual)));
    }

    public Reporte<VentaVendedorFila> porVendedor(Periodo p) {
        List<VentaVendedorFila> filas = jdbc.query("""
                select %s vendedor,
                       count(*) filter (where c.tipo_comprobante in %s) comprobantes,
                       coalesce(sum(%s * u.unidades), 0) prendas,
                       coalesce(sum(c.total) filter (where c.tipo_comprobante in %s), 0) ventas,
                       coalesce(sum(c.total) filter (where c.tipo_comprobante = '07'), 0) nc,
                       coalesce(sum(c.total) filter (where c.tipo_comprobante = '08'), 0) nd,
                       coalesce(sum(c.total_descuento) filter (where c.tipo_comprobante in %s), 0) descuentos,
                       count(*) filter (where c.por_mayor_manual and c.tipo_comprobante in %s) por_mayor_manual
                from comprobante c
                %s
                left join (select comprobante_id, sum(cantidad) unidades from comprobante_detalle
                           where variante_id is not null group by comprobante_id) u on u.comprobante_id = c.id
                where %s
                group by 1
                order by ventas desc
                """.formatted(VENDEDOR, VENTAS, SIGNO, VENTAS, VENTAS, VENTAS, UNIONES_VENDEDOR, vigentes(true)),
                parametros(p), (rs, i) -> {
                    long comprobantes = rs.getLong(2);
                    BigDecimal ventas = dinero(rs, 4);
                    BigDecimal nc = dinero(rs, 5);
                    BigDecimal nd = dinero(rs, 6);
                    return new VentaVendedorFila(rs.getString(1), comprobantes, rs.getLong(3), ventas, nc, nd,
                            ventas.subtract(nc).add(nd),
                            comprobantes == 0 ? BigDecimal.ZERO.setScale(2) : ventas.divide(BigDecimal.valueOf(comprobantes), 2, RoundingMode.HALF_UP),
                            dinero(rs, 7), rs.getLong(8));
                });
        return new Reporte<>("ventas-por-vendedor", "Ventas por vendedor", p.desde(), p.hasta(), filas, List.of(
                Columna.texto("Vendedor", VentaVendedorFila::vendedor),
                Columna.entero("Comprobantes", VentaVendedorFila::comprobantes),
                Columna.entero("Prendas", VentaVendedorFila::prendas),
                Columna.moneda("Ventas (S/)", VentaVendedorFila::ventas),
                Columna.moneda("Notas de crédito (S/)", VentaVendedorFila::notasCredito),
                Columna.moneda("Notas de débito (S/)", VentaVendedorFila::notasDebito),
                Columna.moneda("Neto (S/)", VentaVendedorFila::neto),
                Columna.moneda("Ticket promedio (S/)", VentaVendedorFila::ticketPromedio),
                Columna.moneda("Descuentos (S/)", VentaVendedorFila::descuentos),
                Columna.entero("Por mayor manual", VentaVendedorFila::porMayorManual)));
    }

    public Reporte<VentaHoraFila> porHora(Periodo p) {
        List<VentaHoraFila> filas = jdbc.query("""
                select extract(hour from c.fecha_emision at time zone 'America/Lima')::int hora, count(*), sum(c.total)
                from comprobante c
                where %s and c.tipo_comprobante in %s
                group by 1 order by 1
                """.formatted(vigentes(false), VENTAS), parametros(p),
                (rs, i) -> new VentaHoraFila(rs.getInt(1), "%02d:00 - %02d:59".formatted(rs.getInt(1), rs.getInt(1)),
                        rs.getLong(2), dinero(rs, 3)));
        return new Reporte<>("ventas-por-hora", "Ventas por hora", p.desde(), p.hasta(), filas, List.of(
                Columna.texto("Hora", VentaHoraFila::rango),
                Columna.entero("Comprobantes", VentaHoraFila::comprobantes),
                Columna.moneda("Total (S/)", VentaHoraFila::total)));
    }

    public Reporte<VentaDiaSemanaFila> porDiaSemana(Periodo p) {
        List<VentaDiaSemanaFila> filas = jdbc.query("""
                select extract(isodow from c.fecha_emision at time zone 'America/Lima')::int dia, count(*), sum(c.total)
                from comprobante c
                where %s and c.tipo_comprobante in %s
                group by 1 order by 1
                """.formatted(vigentes(false), VENTAS), parametros(p),
                (rs, i) -> new VentaDiaSemanaFila(rs.getInt(1), DIAS[rs.getInt(1)], rs.getLong(2), dinero(rs, 3)));
        return new Reporte<>("ventas-por-dia-semana", "Ventas por día de la semana", p.desde(), p.hasta(), filas, List.of(
                Columna.texto("Día", VentaDiaSemanaFila::nombre),
                Columna.entero("Comprobantes", VentaDiaSemanaFila::comprobantes),
                Columna.moneda("Total (S/)", VentaDiaSemanaFila::total)));
    }

    public Reporte<VentaDiaFila> porDia(Periodo p) {
        List<VentaDiaFila> filas = jdbc.query("""
                select (c.fecha_emision at time zone 'America/Lima')::date dia,
                       count(*) filter (where c.tipo_comprobante in %s),
                       coalesce(sum(%s * u.unidades), 0),
                       coalesce(sum(c.total) filter (where c.tipo_comprobante in %s), 0),
                       coalesce(sum(c.total) filter (where c.tipo_comprobante = '07'), 0),
                       coalesce(sum(c.total) filter (where c.tipo_comprobante = '08'), 0)
                from comprobante c
                left join (select comprobante_id, sum(cantidad) unidades from comprobante_detalle
                           where variante_id is not null group by comprobante_id) u on u.comprobante_id = c.id
                where %s
                group by 1 order by 1
                """.formatted(VENTAS, SIGNO, VENTAS, vigentes(true)), parametros(p),
                (rs, i) -> new VentaDiaFila(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), dinero(rs, 4),
                        dinero(rs, 5), dinero(rs, 4).subtract(dinero(rs, 5)).add(dinero(rs, 6))));
        return new Reporte<>("ventas-por-dia", "Ventas por día", p.desde(), p.hasta(), filas, List.of(
                Columna.fecha("Fecha", VentaDiaFila::fecha),
                Columna.entero("Comprobantes", VentaDiaFila::comprobantes),
                Columna.entero("Prendas", VentaDiaFila::prendas),
                Columna.moneda("Ventas (S/)", VentaDiaFila::ventas),
                Columna.moneda("Notas de crédito (S/)", VentaDiaFila::notasCredito),
                Columna.moneda("Neto (S/)", VentaDiaFila::neto)));
    }

    /**
     * Prendas con stock que no se venden hace {@code dias} días o más (o nunca). Mira toda la
     * historia de ventas, no solo un periodo.
     */
    public Reporte<SinRotacionFila> sinRotacion(int dias, Long establecimientoId) {
        if (dias < 1 || dias > 3650) {
            throw new BusinessException("Los días sin vender deben estar entre 1 y 3650");
        }
        OffsetDateTime ahora = OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA);
        MapSqlParameterSource params = new MapSqlParameterSource("est", establecimientoId)
                .addValue("limite", ahora.minusDays(dias));
        List<SinRotacionFila> filas = jdbc.query("""
                with ultima as (
                    select d.variante_id, max(c.fecha_emision) fecha
                    from comprobante_detalle d join comprobante c on c.id = d.comprobante_id
                    where c.tipo_comprobante in ('NV', '03', '01') and c.estado in ('EMITIDO', 'CONVERTIDO')
                      and (cast(:est as bigint) is null or c.establecimiento_id = :est)
                    group by d.variante_id),
                existencias as (
                    select variante_id, sum(cantidad) cantidad from stock
                    where cast(:est as bigint) is null or establecimiento_id = :est
                    group by variante_id)
                select v.id, v.sku, pr.nombre, t.codigo, co.nombre, e.cantidad,
                       coalesce(v.precio_venta, pr.precio_venta) precio, u.fecha, v.created_at
                from existencias e
                join producto_variante v on v.id = e.variante_id
                join producto pr on pr.id = v.producto_id
                join talla t on t.id = v.talla_id
                join color co on co.id = v.color_id
                left join ultima u on u.variante_id = v.id
                where e.cantidad > 0 and v.activo and coalesce(u.fecha, v.created_at) < :limite
                order by coalesce(u.fecha, v.created_at), e.cantidad desc
                """, params, (rs, i) -> {
            BigDecimal precio = dinero(rs, 7);
            long stock = rs.getLong(6);
            OffsetDateTime ultima = fecha(rs.getTimestamp(8));
            OffsetDateTime referencia = ultima != null ? ultima : fecha(rs.getTimestamp(9));
            return new SinRotacionFila(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    stock, precio, precio.multiply(BigDecimal.valueOf(stock)).setScale(2, RoundingMode.HALF_UP), ultima,
                    Duration.between(referencia, ahora).toDays());
        });
        return new Reporte<>("sin-rotacion", "Prendas sin rotación (" + dias + " días)", null, null, filas, List.of(
                Columna.texto("SKU", SinRotacionFila::sku),
                Columna.texto("Producto", SinRotacionFila::producto),
                Columna.texto("Talla", SinRotacionFila::talla),
                Columna.texto("Color", SinRotacionFila::color),
                Columna.entero("Stock", SinRotacionFila::stock),
                Columna.moneda("Precio (S/)", SinRotacionFila::precio),
                Columna.moneda("Valor del stock (S/)", SinRotacionFila::valorStock),
                Columna.fechaHora("Última venta", SinRotacionFila::ultimaVenta),
                Columna.entero("Días sin vender", SinRotacionFila::diasSinVender)));
    }

    /** Cada línea de cada comprobante del periodo, vigente o no, con su contexto: para analizar en Excel o Python. */
    public Reporte<DetalleVentaFila> detalle(Periodo p) {
        List<DetalleVentaFila> filas = jdbc.query("""
                select c.id, c.fecha_emision, c.tipo_comprobante, c.serie || '-' || lpad(c.correlativo::text, 8, '0'),
                       c.estado, c.estado = 'EMITIDO', case when c.tipo_comprobante = '07' then -1 else 1 end,
                       e.nombre, %s, c.cliente_tipo_documento, c.cliente_numero_documento, c.cliente_nombre,
                       d.sku, pr.nombre, ca.nombre, t.codigo, co.nombre, d.cantidad, d.precio_unitario, d.descuento,
                       d.valor_venta, d.igv, d.total, c.por_mayor,
                       (select string_agg(distinct pg.metodo, ',') from comprobante_pago pg where pg.comprobante_id = c.id)
                from comprobante_detalle d
                join comprobante c on c.id = d.comprobante_id
                join establecimiento e on e.id = c.establecimiento_id
                %s
                left join producto_variante v on v.id = d.variante_id
                left join producto pr on pr.id = v.producto_id
                left join categoria ca on ca.id = pr.categoria_id
                left join talla t on t.id = v.talla_id
                left join color co on co.id = v.color_id
                where c.fecha_emision >= :desde and c.fecha_emision < :hasta
                  and (cast(:est as bigint) is null or c.establecimiento_id = :est)
                order by c.fecha_emision, c.id, d.item
                """.formatted(VENDEDOR, UNIONES_VENDEDOR), parametros(p), (rs, i) -> new DetalleVentaFila(rs.getLong(1),
                fecha(rs.getTimestamp(2)), nombreTipo(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getBoolean(6),
                rs.getInt(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getInt(18),
                dinero(rs, 19), dinero(rs, 20), dinero(rs, 21), dinero(rs, 22), dinero(rs, 23), rs.getBoolean(24),
                rs.getString(25)));
        return new Reporte<>("detalle-ventas", "Detalle de ventas", p.desde(), p.hasta(), filas, List.of(
                Columna.entero("comprobante_id", DetalleVentaFila::comprobanteId),
                Columna.fechaHora("fecha", DetalleVentaFila::fecha),
                Columna.texto("tipo", DetalleVentaFila::tipo),
                Columna.texto("documento", DetalleVentaFila::documento),
                Columna.texto("estado", DetalleVentaFila::estado),
                Columna.siNo("vigente", DetalleVentaFila::vigente),
                Columna.entero("signo", DetalleVentaFila::signo),
                Columna.texto("establecimiento", DetalleVentaFila::establecimiento),
                Columna.texto("vendedor", DetalleVentaFila::vendedor),
                Columna.texto("cliente_tipo_documento", DetalleVentaFila::clienteTipoDocumento),
                Columna.texto("cliente_documento", DetalleVentaFila::clienteDocumento),
                Columna.texto("cliente_nombre", DetalleVentaFila::clienteNombre),
                Columna.texto("sku", DetalleVentaFila::sku),
                Columna.texto("producto", DetalleVentaFila::producto),
                Columna.texto("categoria", DetalleVentaFila::categoria),
                Columna.texto("talla", DetalleVentaFila::talla),
                Columna.texto("color", DetalleVentaFila::color),
                Columna.entero("cantidad", DetalleVentaFila::cantidad),
                Columna.moneda("precio_unitario", DetalleVentaFila::precioUnitario),
                Columna.moneda("descuento", DetalleVentaFila::descuento),
                Columna.moneda("valor_venta", DetalleVentaFila::valorVenta),
                Columna.moneda("igv", DetalleVentaFila::igv),
                Columna.moneda("total", DetalleVentaFila::total),
                Columna.siNo("por_mayor", DetalleVentaFila::porMayor),
                Columna.texto("metodos_pago", DetalleVentaFila::metodosPago)));
    }

    // ---------- SQL común ----------

    /** -1 en las notas de crédito (devoluciones): restan unidades e importes. */
    private static final String SIGNO = "(case when c.tipo_comprobante = '07' then -1 else 1 end)";

    /** Líneas de comprobante con su comprobante. */
    private static final String LINEAS = """
                from comprobante_detalle d
                join comprobante c on c.id = d.comprobante_id
            """;

    /** Condición de esas líneas: prendas vendidas o devueltas, vigentes, del periodo. */
    private static final String LINEAS_VIGENTES = """
                where c.estado = 'EMITIDO' and c.tipo_comprobante in ('NV', '03', '01', '07') and d.variante_id is not null
                  and c.fecha_emision >= :desde and c.fecha_emision < :hasta
                  and (cast(:est as bigint) is null or c.establecimiento_id = :est)
            """;

    /** Comprobantes vigentes del periodo; con notas, también las de crédito y débito. */
    private static String vigentes(boolean conNotas) {
        return "c.estado = 'EMITIDO' and c.tipo_comprobante in " + (conNotas ? "('NV', '03', '01', '07', '08')" : VENTAS)
                + " and c.fecha_emision >= :desde and c.fecha_emision < :hasta"
                + " and (cast(:est as bigint) is null or c.establecimiento_id = :est)";
    }

    private static MapSqlParameterSource parametros(Periodo p) {
        return new MapSqlParameterSource()
                .addValue("desde", p.desde().atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime())
                .addValue("hasta", p.hasta().plusDays(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime())
                .addValue("est", p.establecimientoId());
    }

    private static BigDecimal dinero(ResultSet rs, int columna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(columna);
        return (valor != null ? valor : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime fecha(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atZone(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
    }

    private static String nombreTipo(String codigo) {
        return switch (codigo) {
            case "NV" -> "NOTA_VENTA";
            case "03" -> "BOLETA";
            case "01" -> "FACTURA";
            case "07" -> "NOTA_CREDITO";
            case "08" -> "NOTA_DEBITO";
            default -> codigo;
        };
    }
}
