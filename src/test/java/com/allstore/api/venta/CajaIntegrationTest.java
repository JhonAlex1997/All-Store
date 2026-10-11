package com.allstore.api.venta;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.dto.CajaResponse;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.caja.dto.MovimientoCajaRequest;
import com.allstore.api.caja.entity.EstadoCaja;
import com.allstore.api.caja.entity.TipoMovimientoCaja;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Apertura, cuadre y cierre de caja (la tienda de prueba empieza con la caja abierta con S/ 100). */
class CajaIntegrationTest extends TiendaDePrueba {

    @Test
    void elCierreCalculaElEfectivoEsperadoYLaDiferencia() {
        // Venta en efectivo (el cliente paga con 200, el vuelto no cuenta), venta con Yape,
        // devolución en efectivo y un egreso.
        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", "200.00")));
        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanB, 1, null)),
                List.of(new PagoRequest(MetodoPago.YAPE, new BigDecimal("89.90"), null, "OP-1")));
        notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta,
                MotivoNotaCredito.DEVOLUCION_POR_ITEM, "Cambio de talla", MetodoPago.EFECTIVO, null,
                List.of(new ItemDevolucionRequest(jeanA, 1))));
        cajaService.registrarMovimiento(new MovimientoCajaRequest(TipoMovimientoCaja.EGRESO,
                new BigDecimal("10.00"), "Taxi"));

        // 100 + 179.80 - 89.90 - 10.00 = 179.90
        CajaResponse antes = cajaService.miCaja();
        assertThat(antes.resumen().cantidadVentas()).isEqualTo(2);
        assertThat(antes.resumen().totalVentas()).isEqualByComparingTo("269.70");
        assertThat(antes.resumen().ventasPorMetodo().get(MetodoPago.EFECTIVO)).isEqualByComparingTo("179.80");
        assertThat(antes.resumen().ventasPorMetodo().get(MetodoPago.YAPE)).isEqualByComparingTo("89.90");
        assertThat(antes.resumen().devolucionesPorMetodo().get(MetodoPago.EFECTIVO)).isEqualByComparingTo("89.90");
        assertThat(antes.resumen().efectivoEsperado()).isEqualByComparingTo("179.90");

        CajaResponse cerrada = cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("180.00"), null));
        assertThat(cerrada.estado()).isEqualTo(EstadoCaja.CERRADA);
        assertThat(cerrada.efectivoContado()).isEqualByComparingTo("180.00");
        assertThat(cerrada.diferencia()).isEqualByComparingTo("0.10");
    }

    @Test
    void sinCajaAbiertaNoSeVende() {
        cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> vender(TipoComprobante.BOLETA, serieBoleta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Abre tu caja");
        assertThat(stock(jeanA, tienda)).isEqualTo(5);
    }

    @Test
    void noSePuedenTenerDosCajasAbiertas() {
        assertThatThrownBy(() -> cajaService.abrir(new AbrirCajaRequest(almacen, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya tienes una caja abierta");
    }

    @Test
    void laCajaDebeSerDelLocalDeLaSerie() {
        Long serieAlmacen = serieService.crear(new com.allstore.api.empresa.dto.SerieCreateRequest(
                TipoComprobante.BOLETA, "B992", almacen, 0L)).id();

        assertThatThrownBy(() -> vender(TipoComprobante.BOLETA, serieAlmacen, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tu caja abierta es de Tienda prueba");
    }

    @Test
    void notaDeVentaDeUnaCajaCerradaYaNoSeAnula() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
        cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("189.90"), null));

        assertThatThrownBy(() -> ventaService.anular(nota.id(), new AnularVentaRequest("tarde")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya se cerró");
    }
}
