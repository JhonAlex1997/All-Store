package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.producto.dto.VariantesRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.reporte.dto.DetalleVentaFila;
import com.allstore.api.reporte.dto.SinRotacionFila;
import com.allstore.api.reporte.dto.VentaTallaColorFila;
import com.allstore.api.reporte.exportacion.Exportador;
import com.allstore.api.reporte.exportacion.Reporte;
import com.allstore.api.reporte.service.ReporteService;
import com.allstore.api.reporte.service.ReporteService.Periodo;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Reportes con un escenario conocido en la tienda de prueba: nota de venta de 2 jeanA, boleta de 2
 * jeanB con una devolución de 1, y una nota de venta anulada (no cuenta).
 */
class ReporteIntegrationTest extends TiendaDePrueba {

    @Autowired private ReporteService reporteService;
    @Autowired private Exportador exportador;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Periodo hoy;
    private VentaResponse boleta;

    @BeforeEach
    void ventasDelDia() {
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Lima"));
        hoy = reporteService.periodo(fecha, fecha, tienda);
        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null)));
        boleta = vender(TipoComprobante.BOLETA, serieBoleta, clienteDni, List.of(item(jeanB, 2, null)), List.of(efectivo("179.80", null)));
        notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM,
                "Cambio de talla", MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanB, 1))));
        VentaResponse anulada = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        anulacionService.anular(anulada.id(), new AnularVentaRequest("Error de digitación"));
        // Los reportes leen con SQL directo: en la prueba (una sola transacción) hay que escribir antes los
        // cambios pendientes de JPA; en el sistema, cada venta ya se confirmó cuando alguien pide un reporte.
        entityManager.flush();
    }

    @Test
    void ventasPorProductoRestanLasDevolucionesYNoCuentanLoAnulado() {
        assertThat(reporteService.porProducto(hoy).filas()).singleElement().satisfies(f -> {
            assertThat(f.codigo()).isEqualTo("PRUEBA-VTA");
            assertThat(f.unidades()).isEqualTo(3);
            assertThat(f.comprobantes()).isEqualTo(2);
            assertThat(f.total()).isEqualByComparingTo("269.70");
            assertThat(f.participacion()).isEqualByComparingTo("100.0");
        });
    }

    @Test
    void porTallaYColorConElStockParaReponer() {
        List<VentaTallaColorFila> filas = reporteService.porTallaColor(hoy).filas();
        assertThat(filas).extracting(VentaTallaColorFila::varianteId, VentaTallaColorFila::unidades, VentaTallaColorFila::stockActual)
                .containsExactly(tuple(jeanA, 2L, 3L), tuple(jeanB, 1L, 9L));
    }

    @Test
    void porVendedorPorDiaYPorHora() {
        assertThat(reporteService.porVendedor(hoy).filas()).singleElement().satisfies(f -> {
            assertThat(f.vendedor()).isEqualTo("sistema");
            assertThat(f.comprobantes()).isEqualTo(2);
            assertThat(f.prendas()).isEqualTo(3);
            assertThat(f.ventas()).isEqualByComparingTo("359.60");
            assertThat(f.notasCredito()).isEqualByComparingTo("89.90");
            assertThat(f.neto()).isEqualByComparingTo("269.70");
            assertThat(f.ticketPromedio()).isEqualByComparingTo("179.80");
        });
        assertThat(reporteService.porDia(hoy).filas()).singleElement().satisfies(f -> {
            assertThat(f.fecha()).isEqualTo(hoy.desde());
            assertThat(f.neto()).isEqualByComparingTo("269.70");
            assertThat(f.prendas()).isEqualTo(3);
        });
        assertThat(reporteService.porHora(hoy).filas()).extracting(f -> f.comprobantes()).containsExactly(2L);
        assertThat(reporteService.porDiaSemana(hoy).filas()).singleElement()
                .satisfies(f -> assertThat(f.dia()).isEqualTo(hoy.desde().getDayOfWeek().getValue()));
    }

    @Test
    void elDetalleTraeTodasLasLineasConSuSignoYSiCuentan() {
        List<DetalleVentaFila> lineas = reporteService.detalle(hoy).filas();
        assertThat(lineas).hasSize(4);
        assertThat(lineas).filteredOn(DetalleVentaFila::vigente)
                .extracting(l -> l.cantidad() * l.signo()).containsExactlyInAnyOrder(2, 2, -1);
        assertThat(lineas).filteredOn(l -> !l.vigente()).singleElement()
                .satisfies(l -> assertThat(l.estado()).isEqualTo("ANULADO"));
        assertThat(lineas).filteredOn(l -> l.tipo().equals("BOLETA")).singleElement().satisfies(l -> {
            assertThat(l.clienteNombre()).isEqualTo("Ana Prueba");
            assertThat(l.metodosPago()).isEqualTo("EFECTIVO");
            assertThat(l.talla()).isNotBlank();
        });
    }

    @Test
    void sinRotacionMuestraLoQueNoSeVendeHaceTiempo() {
        // jeanA y jeanB se vendieron hoy. Un tercer color que nunca se vendió, registrado hace 90 días:
        Long viejo = productoService.agregarVariantes(productoDe(jeanA),
                new VariantesRequest(List.of(3L), List.of(2L))).variantes().stream()
                .filter(v -> !v.id().equals(jeanA) && !v.id().equals(jeanB)).findFirst().orElseThrow().id();
        inventarioService.registrar(tienda, MotivoMovimiento.INVENTARIO_INICIAL,
                List.of(new InventarioService.Linea(viejo, 4)), null);
        entityManager.flush();
        jdbc.update("update producto_variante set created_at = now() - interval '90 days' where id = ?", viejo);

        List<SinRotacionFila> filas = reporteService.sinRotacion(60, tienda).filas();
        assertThat(filas).extracting(SinRotacionFila::varianteId).containsExactly(viejo);
        assertThat(filas.getFirst().ultimaVenta()).isNull();
        assertThat(filas.getFirst().diasSinVender()).isGreaterThanOrEqualTo(89);
        assertThat(filas.getFirst().valorStock()).isEqualByComparingTo(new BigDecimal("359.60"));
    }

    @Test
    void seExportaAExcelYACsvConLasMismasColumnas() throws Exception {
        Reporte<?> reporte = reporteService.porVendedor(hoy);
        byte[] xlsx = exportador.xlsx(reporte);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Vendedor");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("sistema");
            assertThat(hoja.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(269.70);
        }
        String csv = new String(exportador.csv(reporteService.detalle(hoy)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿comprobante_id,fecha,tipo,documento");
        assertThat(csv.split("\r\n")).hasSize(5);
        assertThat(reporte.archivo("xlsx")).isEqualTo("ventas-por-vendedor_" + hoy.desde() + "_" + hoy.hasta() + ".xlsx");
    }

    @Test
    void elPeriodoSeValida() {
        LocalDate hoyLima = LocalDate.now(ZoneId.of("America/Lima"));
        assertThat(reporteService.periodo(null, null, null).desde()).isEqualTo(hoyLima.withDayOfMonth(1));
        assertThatThrownBy(() -> reporteService.periodo(hoyLima, hoyLima.minusDays(1), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no puede ser posterior");
        assertThatThrownBy(() -> reporteService.periodo(hoyLima.minusDays(400), hoyLima, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("hasta 366 días");
    }

    private Long productoDe(Long varianteId) {
        return jdbc.queryForObject("select producto_id from producto_variante where id = ?", Long.class, varianteId);
    }
}
