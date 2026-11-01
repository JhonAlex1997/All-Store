package com.allstore.api.contabilidad.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.contabilidad.dto.AsientoResponse;
import com.allstore.api.contabilidad.dto.LibroDiarioResponse;
import com.allstore.api.contabilidad.service.PlanCuentas.Cuenta;
import com.allstore.api.reporte.exportacion.Columna;
import com.allstore.api.reporte.exportacion.Reporte;
import com.allstore.api.venta.entity.MetodoPago;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Asientos contables del periodo, generados al consultar desde los documentos (no se guardan: siempre
 * coinciden con lo registrado). Todo es al contado:
 * <ul>
 *   <li><b>Venta</b> (nota de venta, boleta, factura vigentes): Debe caja o banco según el método de
 *       pago / Haber 70 (base, exonerado e inafecto) y 40 (IGV). La <b>nota de crédito</b>, al revés; la
 *       <b>nota de débito</b>, como una venta.</li>
 *   <li><b>Costo de ventas</b>, solo de las prendas con costo: Debe 69 / Haber 20 (al costo actual).</li>
 *   <li><b>Compra</b>: Debe 60 (prendas), 60 (insumos), 63 (servicios) y 40 (IGV de facturas) / Haber 42;
 *       y su destino: Debe 20 o 24 / Haber 61.</li>
 *   <li><b>Producción terminada</b>, con su costo: Debe 21 / Haber 71.</li>
 * </ul>
 * Es un borrador para el contador: las cuentas están en {@link PlanCuentas}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsientoService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final NamedParameterJdbcTemplate jdbc;

    public LibroDiarioResponse libroDiario(YearMonth periodo) {
        if (periodo.isAfter(YearMonth.now(JpaAuditingConfig.ZONA_LIMA))) {
            throw new BusinessException("El periodo " + periodo + " todavía no empieza");
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", periodo.atDay(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime())
                .addValue("hasta", periodo.plusMonths(1).atDay(1).atStartOfDay(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime())
                .addValue("periodo", periodo.toString());
        List<String> advertencias = new ArrayList<>();
        List<Borrador> borradores = new ArrayList<>();
        ventas(p, borradores);
        costoDeVentas(p, borradores, advertencias);
        compras(p, borradores);
        produccion(p, borradores);
        borradores.sort(Comparator.comparing(Borrador::fecha).thenComparing(Borrador::orden));

        List<AsientoResponse> asientos = new ArrayList<>();
        for (Borrador b : borradores) {
            AsientoResponse a = new AsientoResponse(asientos.size() + 1, b.fecha(), b.origen(), b.documento(), b.glosa(), b.lineas());
            if (a.debe().compareTo(a.haber()) != 0) {
                advertencias.add("El asiento de " + a.documento() + " no cuadra (debe " + a.debe() + ", haber " + a.haber() + ")");
            }
            asientos.add(a);
        }
        Map<String, BigDecimal[]> totales = new TreeMap<>();
        Map<String, String> nombres = new LinkedHashMap<>();
        asientos.forEach(a -> a.lineas().forEach(l -> {
            BigDecimal[] t = totales.computeIfAbsent(l.cuenta(), c -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(l.debe());
            t[1] = t[1].add(l.haber());
            nombres.put(l.cuenta(), l.denominacion());
        }));
        List<LibroDiarioResponse.Mayor> mayor = totales.entrySet().stream().map(e -> new LibroDiarioResponse.Mayor(e.getKey(),
                nombres.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[0].subtract(e.getValue()[1]))).toList();
        BigDecimal debe = asientos.stream().map(AsientoResponse::debe).reduce(CERO, BigDecimal::add);
        BigDecimal haber = asientos.stream().map(AsientoResponse::haber).reduce(CERO, BigDecimal::add);
        return new LibroDiarioResponse(periodo.toString(), asientos, mayor, debe, haber, advertencias);
    }

    /** El libro diario línea por línea, para Excel o para el sistema contable. */
    public Reporte<FilaDiario> reporte(YearMonth periodo) {
        List<FilaDiario> filas = new ArrayList<>();
        libroDiario(periodo).asientos().forEach(a -> a.lineas().forEach(l -> filas.add(new FilaDiario(a.numero(), a.fecha(),
                a.documento(), a.glosa(), l.cuenta(), l.denominacion(), l.debe(), l.haber()))));
        return new Reporte<>("libro-diario-" + periodo, "Libro diario " + periodo, periodo.atDay(1), periodo.atEndOfMonth(), filas, List.of(
                Columna.entero("Asiento", FilaDiario::asiento),
                Columna.fecha("Fecha", FilaDiario::fecha),
                Columna.texto("Documento", FilaDiario::documento),
                Columna.texto("Glosa", FilaDiario::glosa),
                Columna.texto("Cuenta", FilaDiario::cuenta),
                Columna.texto("Denominación", FilaDiario::denominacion),
                Columna.moneda("Debe", FilaDiario::debe),
                Columna.moneda("Haber", FilaDiario::haber)));
    }

    public record FilaDiario(int asiento, LocalDate fecha, String documento, String glosa, String cuenta, String denominacion,
                             BigDecimal debe, BigDecimal haber) {
    }

    // ---------- Ventas ----------

    private void ventas(MapSqlParameterSource p, List<Borrador> borradores) {
        Map<Long, List<Object[]>> pagos = new LinkedHashMap<>();
        jdbc.query("""
                select pg.comprobante_id, pg.metodo, pg.monto from comprobante_pago pg join comprobante c on c.id = pg.comprobante_id
                where c.estado = 'EMITIDO' and c.fecha_emision >= :desde and c.fecha_emision < :hasta
                """, p, rs -> {
            pagos.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Object[]{MetodoPago.valueOf(rs.getString(2)), rs.getBigDecimal(3)});
        });
        jdbc.query("""
                select c.id, c.tipo_comprobante, c.serie || '-' || lpad(c.correlativo::text, 8, '0'),
                       (c.fecha_emision at time zone 'America/Lima')::date, c.op_gravadas, c.op_exoneradas, c.op_inafectas,
                       c.total_igv, c.total, c.cliente_nombre
                from comprobante c
                where c.estado = 'EMITIDO' and c.tipo_comprobante in ('NV', '03', '01', '07', '08')
                  and c.fecha_emision >= :desde and c.fecha_emision < :hasta
                order by c.fecha_emision, c.id
                """, p, rs -> {
            String tipo = rs.getString(2);
            boolean nota = tipo.equals("07");
            BigDecimal ingresos = dinero(rs.getBigDecimal(5)).add(dinero(rs.getBigDecimal(6))).add(dinero(rs.getBigDecimal(7)));
            BigDecimal igv = dinero(rs.getBigDecimal(8));
            String documento = nombreTipo(tipo) + " " + rs.getString(3);
            List<AsientoResponse.Linea> lineas = new ArrayList<>();
            List<Object[]> cobros = pagos.getOrDefault(rs.getLong(1), List.of());
            Map<Cuenta, BigDecimal> porCuenta = new LinkedHashMap<>();
            for (Object[] pago : cobros) {
                porCuenta.merge(PlanCuentas.cobro((MetodoPago) pago[0]), dinero((BigDecimal) pago[1]), BigDecimal::add);
            }
            if (!nota) {
                porCuenta.forEach((cuenta, monto) -> lineas.add(debe(cuenta, monto)));
                lineas.add(haber(PlanCuentas.VENTAS, ingresos));
                if (igv.signum() > 0) {
                    lineas.add(haber(PlanCuentas.IGV, igv));
                }
            } else {
                lineas.add(debe(PlanCuentas.VENTAS, ingresos));
                if (igv.signum() > 0) {
                    lineas.add(debe(PlanCuentas.IGV, igv));
                }
                porCuenta.forEach((cuenta, monto) -> lineas.add(haber(cuenta, monto)));
            }
            String origen = switch (tipo) {
                case "07" -> "NOTA_CREDITO";
                case "08" -> "NOTA_DEBITO";
                default -> "VENTA";
            };
            String glosa = (nota ? "Devolución a " : "Venta al contado a ") + rs.getString(10);
            borradores.add(new Borrador(rs.getDate(4).toLocalDate(), 1, origen, documento, glosa, lineas));
        });
    }

    /** Costo de lo vendido (menos lo devuelto) del día, solo de las prendas con costo; las demás se advierten. */
    private void costoDeVentas(MapSqlParameterSource p, List<Borrador> borradores, List<String> advertencias) {
        long sinCosto = 0;
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        List<Object[]> filas = jdbc.query("""
                select (c.fecha_emision at time zone 'America/Lima')::date,
                       case when c.tipo_comprobante = '07' then -1 else 1 end * d.cantidad,
                       coalesce(v.costo, pr.costo)
                from comprobante_detalle d
                join comprobante c on c.id = d.comprobante_id
                join producto_variante v on v.id = d.variante_id
                join producto pr on pr.id = v.producto_id
                where c.estado = 'EMITIDO' and c.tipo_comprobante in ('NV', '03', '01', '07')
                  and c.fecha_emision >= :desde and c.fecha_emision < :hasta
                """, p, (rs, i) -> new Object[]{rs.getDate(1).toLocalDate(), rs.getInt(2), rs.getBigDecimal(3)});
        for (Object[] f : filas) {
            if (f[2] == null) {
                sinCosto += Math.abs((int) f[1]);
                continue;
            }
            porDia.merge((LocalDate) f[0], ((BigDecimal) f[2]).multiply(BigDecimal.valueOf((int) f[1])), BigDecimal::add);
        }
        porDia.forEach((dia, costo) -> {
            BigDecimal monto = costo.setScale(2, RoundingMode.HALF_UP);
            if (monto.signum() > 0) {
                borradores.add(new Borrador(dia, 2, "COSTO_VENTA", "Ventas del " + dia, "Costo de las prendas vendidas",
                        List.of(debe(PlanCuentas.COSTO_VENTAS, monto), haber(PlanCuentas.MERCADERIAS, monto))));
            }
        });
        if (sinCosto > 0) {
            advertencias.add(sinCosto + " prenda(s) vendidas o devueltas no tienen costo: su costo de ventas no se contabilizó. "
                    + "Ingresa el costo del modelo o de la prenda para incluirlas");
        }
    }

    // ---------- Compras ----------

    private void compras(MapSqlParameterSource p, List<Borrador> borradores) {
        jdbc.query("""
                select c.id, c.fecha_emision, c.tipo_documento, c.serie || '-' || c.numero, pv.razon_social, c.igv, c.total,
                       coalesce(sum(d.valor) filter (where d.variante_id is not null), 0),
                       coalesce(sum(d.valor) filter (where d.insumo_id is not null), 0),
                       coalesce(sum(d.valor) filter (where d.variante_id is null and d.insumo_id is null), 0)
                from compra c
                join proveedor pv on pv.id = c.proveedor_id
                join compra_detalle d on d.compra_id = c.id
                where c.estado = 'REGISTRADA' and c.periodo = :periodo
                group by c.id, c.fecha_emision, c.tipo_documento, c.serie, c.numero, pv.razon_social, c.igv, c.total
                order by c.fecha_emision, c.id
                """, p, rs -> {
            LocalDate fecha = rs.getDate(2).toLocalDate();
            String documento = "Compra " + rs.getString(4);
            BigDecimal prendas = dinero(rs.getBigDecimal(8));
            BigDecimal insumos = dinero(rs.getBigDecimal(9));
            BigDecimal servicios = dinero(rs.getBigDecimal(10));
            BigDecimal igv = dinero(rs.getBigDecimal(6));
            List<AsientoResponse.Linea> lineas = new ArrayList<>();
            if (prendas.signum() > 0) {
                lineas.add(debe(PlanCuentas.COMPRA_MERCADERIAS, prendas));
            }
            if (insumos.signum() > 0) {
                lineas.add(debe(PlanCuentas.COMPRA_MATERIAS_PRIMAS, insumos));
            }
            if (servicios.signum() > 0) {
                lineas.add(debe(PlanCuentas.SERVICIOS, servicios));
            }
            if (igv.signum() > 0) {
                lineas.add(debe(PlanCuentas.IGV, igv));
            }
            lineas.add(haber(PlanCuentas.PROVEEDORES, dinero(rs.getBigDecimal(7))));
            borradores.add(new Borrador(fecha, 0, "COMPRA", documento, "Compra a " + rs.getString(5), lineas));

            List<AsientoResponse.Linea> destino = new ArrayList<>();
            if (prendas.signum() > 0) {
                destino.add(debe(PlanCuentas.MERCADERIAS, prendas));
                destino.add(haber(PlanCuentas.VARIACION_MERCADERIAS, prendas));
            }
            if (insumos.signum() > 0) {
                destino.add(debe(PlanCuentas.MATERIAS_PRIMAS, insumos));
                destino.add(haber(PlanCuentas.VARIACION_MATERIAS_PRIMAS, insumos));
            }
            if (!destino.isEmpty()) {
                borradores.add(new Borrador(fecha, 0, "DESTINO_COMPRA", documento, "Ingreso al almacén", destino));
            }
        });
    }

    // ---------- Producción ----------

    private void produccion(MapSqlParameterSource p, List<Borrador> borradores) {
        jdbc.query("""
                select o.id, (o.fecha_fin at time zone 'America/Lima')::date, coalesce(o.costo_insumos, 0) + coalesce(o.costo_servicios, 0),
                       o.unidades_terminadas
                from orden_produccion o
                where o.estado = 'TERMINADA' and o.fecha_fin >= :desde and o.fecha_fin < :hasta
                """, p, rs -> {
            BigDecimal costo = dinero(rs.getBigDecimal(3));
            if (costo.signum() > 0) {
                borradores.add(new Borrador(rs.getDate(2).toLocalDate(), 3, "PRODUCCION", "Orden de producción N° " + rs.getLong(1),
                        "Ingreso de " + rs.getInt(4) + " prendas producidas", List.of(debe(PlanCuentas.PRODUCTOS_TERMINADOS, costo),
                        haber(PlanCuentas.VARIACION_PRODUCTOS, costo))));
            }
        });
    }

    // ---------- Apoyo ----------

    private record Borrador(LocalDate fecha, int orden, String origen, String documento, String glosa, List<AsientoResponse.Linea> lineas) {
    }

    private static AsientoResponse.Linea debe(Cuenta cuenta, BigDecimal monto) {
        return new AsientoResponse.Linea(cuenta.codigo(), cuenta.denominacion(), monto.setScale(2, RoundingMode.HALF_UP), CERO);
    }

    private static AsientoResponse.Linea haber(Cuenta cuenta, BigDecimal monto) {
        return new AsientoResponse.Linea(cuenta.codigo(), cuenta.denominacion(), CERO, monto.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? CERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String nombreTipo(String codigo) {
        return switch (codigo) {
            case "NV" -> "Nota de venta";
            case "03" -> "Boleta";
            case "01" -> "Factura";
            case "07" -> "Nota de crédito";
            case "08" -> "Nota de débito";
            default -> codigo;
        };
    }
}
