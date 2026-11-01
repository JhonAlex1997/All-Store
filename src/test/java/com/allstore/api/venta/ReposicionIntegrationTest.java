package com.allstore.api.venta;

import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.AlertaService;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.dto.ReposicionFila;
import com.allstore.api.inventario.dto.StockMinimoRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.inventario.service.ReposicionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Stock mínimo del modelo (en las tiendas) y propio de una prenda en un local; lista de reposición y alerta. */
class ReposicionIntegrationTest extends TiendaDePrueba {

    @Autowired private ReposicionService reposicionService;
    @Autowired private AlertaService alertaService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private Long modelo;

    @BeforeEach
    void modelo() {
        modelo = jdbc.queryForObject("select producto_id from producto_variante where id = ?", Long.class, jeanA);
    }

    @Test
    void sinMinimoNoHayNadaQueReponer() {
        assertThat(pendientes(tienda)).isEmpty();
    }

    @Test
    void elMinimoDelModeloRigeEnLasTiendasYAvisaLoQueLlegoAEl() {
        productoService.definirStockMinimo(modelo, 6);
        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null)));
        inventarioService.registrar(almacen, MotivoMovimiento.INVENTARIO_INICIAL, List.of(new InventarioService.Linea(jeanA, 4)), null);
        entityManager.flush();

        // jeanA: 5 - 2 = 3, bajo su mínimo de 6. jeanB: 10, sobre el mínimo. El almacén no usa el mínimo del modelo.
        assertThat(pendientes(tienda)).singleElement().satisfies(f -> {
            assertThat(f.varianteId()).isEqualTo(jeanA);
            assertThat(f.stock()).isEqualTo(3);
            assertThat(f.minimo()).isEqualTo(6);
            assertThat(f.minimoPropio()).isFalse();
            assertThat(f.faltan()).isEqualTo(3);
            assertThat(f.vendidas30Dias()).isEqualTo(2);
            assertThat(f.enOtrosLocales()).isEqualTo(4);
        });
        assertThat(pendientes(almacen)).isEmpty();
    }

    @Test
    void elMinimoPropioMandaSobreElDelModeloYSePuedeQuitar() {
        productoService.definirStockMinimo(modelo, 2);
        List<ReposicionFila> tras = reposicionService.definirMinimos(new StockMinimoRequest(tienda,
                List.of(new StockMinimoRequest.Item(jeanB, 12))));
        assertThat(tras).singleElement().satisfies(f -> {
            assertThat(f.varianteId()).isEqualTo(jeanB);
            assertThat(f.minimo()).isEqualTo(12);
            assertThat(f.minimoPropio()).isTrue();
            assertThat(f.faltan()).isEqualTo(2);
        });

        // En el almacén, un mínimo propio sí aplica: jeanA no tiene stock ahí (agotada).
        reposicionService.definirMinimos(new StockMinimoRequest(almacen, List.of(new StockMinimoRequest.Item(jeanA, 1))));
        assertThat(pendientes(almacen)).singleElement().satisfies(f -> assertThat(f.agotada()).isTrue());

        reposicionService.definirMinimos(new StockMinimoRequest(tienda, List.of(new StockMinimoRequest.Item(jeanB, null))));
        assertThat(pendientes(tienda)).isEmpty();
    }

    @Test
    void laAlertaEsUnaPorLocalNoUnaPorPrenda() {
        productoService.definirStockMinimo(modelo, 10);
        entityManager.flush();

        List<Alerta> alertas = alertaService.alertas().alertas().stream()
                .filter(a -> a.tipo() == Tipo.STOCK_BAJO && a.mensaje().startsWith("Tienda prueba:")).toList();
        assertThat(alertas).singleElement().satisfies(a ->
                assertThat(a.mensaje()).startsWith("Tienda prueba: 2 prendas están en o bajo su stock mínimo"));
    }

    private List<ReposicionFila> pendientes(Long local) {
        return reposicionService.reporte(local).filas();
    }
}
