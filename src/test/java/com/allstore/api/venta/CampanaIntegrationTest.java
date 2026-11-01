package com.allstore.api.venta;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.producto.dto.ProductoUpdateRequest;
import com.allstore.api.promocion.dto.CampanaRequest;
import com.allstore.api.promocion.dto.CampanaResponse;
import com.allstore.api.promocion.entity.TipoCampana;
import com.allstore.api.promocion.service.CampanaService;
import com.allstore.api.reporte.service.ReporteService;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Campañas: se aplican solas en la venta, gana el mejor precio y no se acumulan con el por mayor. */
class CampanaIntegrationTest extends TiendaDePrueba {

    @Autowired private CampanaService campanaService;
    @Autowired private ReporteService reporteService;
    @Autowired private Bitacora bitacora;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private LocalDate hoy;
    private Long modelo;

    @BeforeEach
    void datos() {
        hoy = LocalDate.now(ZoneId.of("America/Lima"));
        modelo = jdbc.queryForObject("select producto_id from producto_variante where id = ?", Long.class, jeanA);
    }

    @Test
    void unaCampanaDelModeloSeAplicaSolaComoDescuentoDeLaLinea() {
        CampanaResponse campana = campana("Liquidación de verano", TipoCampana.PORCENTAJE, "20", tienda, List.of(modelo), List.of());

        // 89.90 - 20 % = 71.92 por prenda; 2 prendas: descuento de campaña 35.96.
        VentaResponse venta = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 2, null)),
                List.of(efectivo("143.84", null)));
        assertThat(venta.detalles()).singleElement().satisfies(d -> {
            assertThat(d.precioUnitario()).isEqualByComparingTo("89.90");
            assertThat(d.descuento()).isEqualByComparingTo("35.96");
            assertThat(d.descuentoCampana()).isEqualByComparingTo("35.96");
            assertThat(d.campanaId()).isEqualTo(campana.id());
            assertThat(d.total()).isEqualByComparingTo("143.84");
        });
        // El descuento de la campaña no va a la bitácora (lo decidió el administrador, no el cajero).
        assertThat(bitacora.consultar(null, null, null, List.of(AccionBitacora.DESCUENTO_EN_VENTA), null, venta.id(),
                PageRequest.of(0, 5)).content()).isEmpty();

        entityManager.flush();
        assertThat(reporteService.porCampana(reporteService.periodo(hoy, hoy, tienda)).filas()).singleElement().satisfies(f -> {
            assertThat(f.unidades()).isEqualTo(2);
            assertThat(f.descuento()).isEqualByComparingTo("35.96");
        });
    }

    @Test
    void ganaElMejorPrecioYNoSeAcumulaConElPorMayor() {
        campana("10 soles menos", TipoCampana.MONTO, "10", null, List.of(), List.of());
        campana("Precio fijo", TipoCampana.PRECIO_FIJO, "75.00", null, List.of(), List.of(jeanB));
        assertThat(campanaService.precios(List.of(jeanA, jeanB), tienda)).extracting(p -> p.precioCampana().toPlainString())
                .containsExactlyInAnyOrder("79.90", "75.00");

        // Con por mayor a 70.00, el por mayor es mejor que la campaña: no se aplica la campaña.
        productoService.actualizar(modelo, new ProductoUpdateRequest("Jean prueba", null, 1L, new BigDecimal("89.90"),
                new BigDecimal("70.00"), true));
        VentaResponse mayor = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, null,
                List.of(item(jeanA, 3, null)), List.of(efectivo("210.00", null)), null, null, null));
        assertThat(mayor.detalles().getFirst().campanaId()).isNull();
        assertThat(mayor.detalles().getFirst().precioUnitario()).isEqualByComparingTo("70.00");
    }

    @Test
    void unaCampanaFueraDeFechasOdeOtroLocalOInactivaNoSeAplica() {
        campanaService.crear(new CampanaRequest("Pasada", null, TipoCampana.PORCENTAJE, new BigDecimal("50"),
                hoy.minusDays(10), hoy.minusDays(1), null, true, null, null, null));
        campanaService.crear(new CampanaRequest("Otro local", null, TipoCampana.PORCENTAJE, new BigDecimal("50"),
                hoy, hoy, almacen, true, null, null, null));
        campanaService.crear(new CampanaRequest("Apagada", null, TipoCampana.PORCENTAJE, new BigDecimal("50"),
                hoy, hoy, null, false, null, null, null));

        VentaResponse venta = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        assertThat(venta.detalles().getFirst().campanaId()).isNull();
        assertThatThrownBy(() -> campanaService.crear(new CampanaRequest("Mal", null, TipoCampana.PORCENTAJE,
                new BigDecimal("100"), hoy, hoy, null, true, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("menor a 100");
    }

    private CampanaResponse campana(String nombre, TipoCampana tipo, String valor, Long local, List<Long> productos,
                                    List<Long> variantes) {
        return campanaService.crear(new CampanaRequest(nombre, null, tipo, new BigDecimal(valor), hoy, hoy.plusDays(7), local,
                true, null, productos, variantes));
    }
}
