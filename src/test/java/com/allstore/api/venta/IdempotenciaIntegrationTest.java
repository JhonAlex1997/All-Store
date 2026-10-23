package com.allstore.api.venta;

import com.allstore.api.caja.dto.MovimientoCajaRequest;
import com.allstore.api.caja.entity.TipoMovimientoCaja;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.dto.MovimientoManualRequest;
import com.allstore.api.inventario.dto.TrasladoRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reintentos con la misma clave Idempotency-Key: devuelven lo ya registrado (comprobante, movimiento
 * de caja, ajuste o traslado de inventario), sin volver a mover stock, caja ni numeración. (Dos solicitudes simultáneas, el doble clic, se prueban con la
 * aplicación en dev: aquí todo corre en una sola transacción.)
 */
class IdempotenciaIntegrationTest extends TiendaDePrueba {

    @Autowired private WebApplicationContext context;

    @Test
    void elReintentoDeUnaVentaDevuelveLaMismaSinDescontarOtraVez() {
        String clave = UUID.randomUUID().toString();
        VentaRequest venta = boleta(2);

        VentaResponse primera = ventaService.crear(venta, clave);
        VentaResponse reintento = ventaService.crear(venta, clave);

        assertThat(reintento.id()).isEqualTo(primera.id());
        assertThat(reintento.numero()).isEqualTo("B991-00000001");
        assertThat(stock(jeanA, tienda)).isEqualTo(3);
        assertThat(cajaService.miCaja().resumen().cantidadVentas()).isEqualTo(1);
        // La numeración tampoco avanzó: la siguiente venta toma el número 2.
        assertThat(ventaService.crear(boleta(1), UUID.randomUUID().toString()).numero()).isEqualTo("B991-00000002");
    }

    @Test
    void sinClaveCadaSolicitudEsUnaVentaNueva() {
        VentaResponse una = ventaService.crear(boleta(1));
        VentaResponse otra = ventaService.crear(boleta(1));

        assertThat(otra.id()).isNotEqualTo(una.id());
        assertThat(stock(jeanA, tienda)).isEqualTo(3);
    }

    @Test
    void laMismaClaveConOtroContenidoEsUnError() {
        String clave = UUID.randomUUID().toString();
        ventaService.crear(boleta(1), clave);

        assertThatThrownBy(() -> ventaService.crear(boleta(2), clave))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya se usó para otra solicitud (Boleta de venta B991-00000001)");
        assertThatThrownBy(() -> ventaService.crear(boleta(1), "corta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("de 8 a 64 caracteres");
    }

    @Test
    void elReintentoDeUnaNotaDeCreditoNoDevuelveDosVecesNiElDineroNiLaPrenda() {
        VentaResponse boleta = ventaService.crear(boleta(2));
        NotaCreditoRequest devolucion = new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM,
                "Cambio de talla", MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanA, 1)));
        String clave = UUID.randomUUID().toString();

        VentaResponse nota = notaCreditoService.emitir(boleta.id(), devolucion, clave);
        VentaResponse reintento = notaCreditoService.emitir(boleta.id(), devolucion, clave);

        assertThat(reintento.id()).isEqualTo(nota.id());
        assertThat(stock(jeanA, tienda)).isEqualTo(4);
        var caja = cajaService.miCaja().resumen();
        assertThat(caja.cantidadNotasCredito()).isEqualTo(1);
        assertThat(caja.totalNotasCredito()).isEqualByComparingTo("89.90");
    }

    @Test
    void elReintentoDeUnaConversionDevuelveLaMismaBoleta() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        ConvertirVentaRequest aBoleta = new ConvertirVentaRequest(TipoComprobante.BOLETA, serieBoleta, null);
        String clave = UUID.randomUUID().toString();

        VentaResponse boleta = ventaService.convertir(nota.id(), aBoleta, clave);

        // Sin la clave fallaría: la nota ya está convertida.
        assertThat(ventaService.convertir(nota.id(), aBoleta, clave).id()).isEqualTo(boleta.id());
        assertThatThrownBy(() -> ventaService.convertir(nota.id(), aBoleta, UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void elReintentoDeUnEgresoDeCajaNoLoRegistraDosVeces() {
        MovimientoCajaRequest taxi = new MovimientoCajaRequest(TipoMovimientoCaja.EGRESO, new BigDecimal("15.00"), "Taxi");
        String clave = UUID.randomUUID().toString();

        cajaService.registrarMovimiento(taxi, clave);
        var caja = cajaService.registrarMovimiento(taxi, clave);

        assertThat(caja.movimientos()).hasSize(1);
        assertThat(caja.resumen().totalEgresos()).isEqualByComparingTo("15.00");
        assertThat(caja.resumen().efectivoEsperado()).isEqualByComparingTo("85.00");
        assertThatThrownBy(() -> cajaService.registrarMovimiento(
                new MovimientoCajaRequest(TipoMovimientoCaja.EGRESO, new BigDecimal("20.00"), "Taxi"), clave))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("movimiento de caja \"Taxi\"");
    }

    @Test
    void elReintentoDeUnAjusteOTrasladoDeInventarioNoMueveElStockDosVeces() {
        MovimientoManualRequest sobrante = new MovimientoManualRequest(tienda, MotivoMovimiento.AJUSTE_ENTRADA, "Conteo",
                List.of(new ItemCantidadRequest(jeanA, 2)));
        String claveAjuste = UUID.randomUUID().toString();
        var ajuste = inventarioService.registrarManual(sobrante, claveAjuste);
        assertThat(inventarioService.registrarManual(sobrante, claveAjuste)).isEqualTo(ajuste);
        assertThat(stock(jeanA, tienda)).isEqualTo(7);

        TrasladoRequest alAlmacen = new TrasladoRequest(tienda, almacen, null, List.of(new ItemCantidadRequest(jeanB, 3)));
        String claveTraslado = UUID.randomUUID().toString();
        var traslado = inventarioService.trasladar(alAlmacen, claveTraslado);
        assertThat(inventarioService.trasladar(alAlmacen, claveTraslado)).isEqualTo(traslado);
        assertThat(stock(jeanB, tienda)).isEqualTo(7);
        assertThat(stock(jeanB, almacen)).isEqualTo(3);

        // La clave de un ajuste no sirve para un traslado distinto, ni al revés.
        assertThatThrownBy(() -> inventarioService.trasladar(alAlmacen, claveAjuste))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se usó");
    }

    @Test
    void porLaApiElReintentoRespondeLaMismaVenta() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String clave = UUID.randomUUID().toString();
        String cuerpo = """
                {"tipoComprobante": "BOLETA", "serieId": %d,
                 "items": [{"varianteId": %d, "cantidad": 1}],
                 "pagos": [{"metodo": "EFECTIVO", "monto": 89.90}]}""".formatted(serieBoleta, jeanA);

        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .header(SolicitudIdempotente.CABECERA, clave))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.numero").value("B991-00000001"));
        }
        assertThat(stock(jeanA, tienda)).isEqualTo(4);
    }

    @Test
    void elNavegadorPuedeEnviarLaCabecera() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(options("/api/ventas")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("idempotency-key")));
    }

    private VentaRequest boleta(int cantidad) {
        return new VentaRequest(TipoComprobante.BOLETA, serieBoleta, null, null, List.of(item(jeanA, cantidad, null)),
                List.of(efectivo(cantidad == 1 ? "89.90" : "179.80", null)), null);
    }
}
