package com.allstore.api.venta;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.producto.dto.VarianteUpdateRequest;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros del listado de ventas y sus totales, por la API (así se prueba también cómo llegan los
 * parámetros). Las consultas se limitan a la tienda de prueba para no ver otras ventas de la base.
 */
class ConsultaVentasIntegrationTest extends TiendaDePrueba {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;
    private String boletaEfectivo;
    private String boletaYapePorMayor;
    private String facturaMixta;
    private String notaAnulada;
    private String notaConvertida;
    private String boletaDeLaNota;
    private String notaCredito;
    private String boletaPorMayorManual;

    /**
     * Un día de ventas: jeanB pasa a tener precio por mayor de S/ 70.00.
     * <ul>
     *   <li>Boleta 1 jeanA, efectivo 89.90 (luego se devuelve con una nota de crédito en efectivo)</li>
     *   <li>Boleta 3 jeanB por mayor, Yape 210.00</li>
     *   <li>Factura 1 jeanA, efectivo 50.00 + Yape 39.90</li>
     *   <li>Nota de venta 1 jeanB, Plin 89.90, anulada</li>
     *   <li>Nota de venta 1 jeanA, efectivo 89.90, convertida en boleta</li>
     *   <li>Boleta 1 jeanB por mayor marcado por el cajero, efectivo 70.00</li>
     * </ul>
     */
    @BeforeEach
    void unDiaDeVentas() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        productoService.actualizarVariante(jeanB, new VarianteUpdateRequest(null, new BigDecimal("70.00"), true));

        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        boletaEfectivo = boleta.numero();
        boletaYapePorMayor = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanB, 3, null)),
                List.of(pago(MetodoPago.YAPE, "210.00"))).numero();
        facturaMixta = vender(TipoComprobante.FACTURA, serieFactura, clienteRuc, List.of(item(jeanA, 1, null)),
                List.of(efectivo("50.00", null), pago(MetodoPago.YAPE, "39.90"))).numero();

        VentaResponse anulada = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanB, 1, null)),
                List.of(pago(MetodoPago.PLIN, "89.90")));
        anulacionService.anular(anulada.id(), new AnularVentaRequest("Prueba de filtros"));
        notaAnulada = anulada.numero();

        VentaResponse convertida = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        notaConvertida = convertida.numero();
        boletaDeLaNota = ventaService.convertir(convertida.id(),
                new ConvertirVentaRequest(TipoComprobante.BOLETA, serieBoleta, null)).numero();

        notaCredito = notaCreditoService.emitir(boleta.id(), new NotaCreditoRequest(serieNcBoleta,
                MotivoNotaCredito.DEVOLUCION_POR_ITEM, "No le quedó", MetodoPago.EFECTIVO, null,
                List.of(new ItemDevolucionRequest(jeanA, 1)))).numero();

        boletaPorMayorManual = ventaService.crear(new VentaRequest(TipoComprobante.BOLETA, serieBoleta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("70.00", null)), true)).numero();
    }

    @Test
    void filtraPorMetodoDePagoAunqueSeaUnPagoMixto() throws Exception {
        listar("metodoPago=YAPE")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaYapePorMayor, facturaMixta)))
                .andExpect(jsonPath("$.content[?(@.numero == '" + facturaMixta + "')].metodosPago[*]",
                        containsInAnyOrder("EFECTIVO", "YAPE")));
        // Varios métodos: separados por coma o repitiendo el parámetro.
        listar("metodoPago=YAPE,PLIN").andExpect(jsonPath("$.content", hasSize(3)));
        listar("metodoPago=YAPE&metodoPago=PLIN").andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    void filtraPorTipoYEstado() throws Exception {
        listar("tipoComprobante=FACTURA,NOTA_CREDITO")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(facturaMixta, notaCredito)));
        listar("estado=ANULADO,CONVERTIDO")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(notaAnulada, notaConvertida)));
    }

    @Test
    void filtraLasVentasPorMayorYLasQueDioElCajero() throws Exception {
        listar("porMayor=true")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaYapePorMayor, boletaPorMayorManual)));
        listar("porMayorManual=true")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaPorMayorManual)))
                .andExpect(jsonPath("$.content[0].porMayorManual").value(true));
    }

    @Test
    void filtraPorFechaVendedorClienteCajaYMonto() throws Exception {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        listar("desde=" + hoy + "&hasta=" + hoy).andExpect(jsonPath("$.content", hasSize(8)));
        listar("hasta=" + hoy.minusDays(1)).andExpect(jsonPath("$.content", hasSize(0)));
        listar("vendedor=SISTEMA").andExpect(jsonPath("$.content", hasSize(8)));
        listar("vendedor=otro").andExpect(jsonPath("$.content", hasSize(0)));
        listar("clienteId=" + clienteRuc).andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(facturaMixta)));
        listar("totalDesde=100&totalHasta=300")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaYapePorMayor)));
        // La boleta de la nota convertida no es de la caja: el dinero se cobró con la nota de venta.
        listar("cajaSesionId=" + cajaService.miCaja().id())
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaEfectivo, boletaYapePorMayor,
                        facturaMixta, notaAnulada, notaConvertida, notaCredito, boletaPorMayorManual)));
    }

    @Test
    void filtraPorEstadoAnteSunatYLasNotasDeVentaNoTienen() throws Exception {
        listar("estadoSunat=PENDIENTE")
                .andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(boletaEfectivo, boletaYapePorMayor,
                        facturaMixta, boletaDeLaNota, notaCredito, boletaPorMayorManual)));
        listar("estadoSunat=ACEPTADO").andExpect(jsonPath("$.content", hasSize(0)));
        listar("busqueda=" + notaAnulada).andExpect(jsonPath("$.content[0].estadoSunat").doesNotExist());
    }

    @Test
    void losTotalesCuentanSoloLoVigenteYLoCobradoPorMetodo() throws Exception {
        consultar("/api/ventas/totales?establecimientoId=" + tienda)
                .andExpect(status().isOk())
                // Boletas 89.90 + 210.00 + 89.90 (de la nota convertida) + 70.00 y la factura 89.90.
                .andExpect(jsonPath("$.cantidadVentas").value(5))
                .andExpect(jsonPath("$.totalVentas").value(549.70))
                .andExpect(jsonPath("$.cantidadNotasCredito").value(1))
                .andExpect(jsonPath("$.totalNotasCredito").value(89.90))
                .andExpect(jsonPath("$.totalNotasDebito").value(0))
                .andExpect(jsonPath("$.totalNeto").value(459.80))
                .andExpect(jsonPath("$.cobradoPorMetodo.EFECTIVO").value(299.80))
                .andExpect(jsonPath("$.cobradoPorMetodo.YAPE").value(249.90))
                .andExpect(jsonPath("$.cobradoPorMetodo.PLIN").doesNotExist())
                .andExpect(jsonPath("$.devueltoPorMetodo.EFECTIVO").value(89.90))
                .andExpect(jsonPath("$.cantidadAnulados").value(1));

        // Con el filtro de Yape la factura mixta suma completa, pero por Yape solo su parte.
        consultar("/api/ventas/totales?establecimientoId=" + tienda + "&metodoPago=YAPE")
                .andExpect(jsonPath("$.cantidadVentas").value(2))
                .andExpect(jsonPath("$.totalVentas").value(299.90))
                .andExpect(jsonPath("$.cobradoPorMetodo.YAPE").value(249.90))
                .andExpect(jsonPath("$.cobradoPorMetodo.EFECTIVO").value(50.00));
    }

    @Test
    void elCajeroVeSoloSusVentasSalvoQueBusqueUnNumeroExacto() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("cajero1", null, "ROLE_CAJERO"));
        try {
            cajaService.abrir(new AbrirCajaRequest(tienda, new BigDecimal("50.00")));
            String suya = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)),
                    List.of(efectivo("89.90", null))).numero();

            listar("").andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(suya)));
            // Pedir las de otro vendedor no cambia nada: el filtro lo pone el servidor.
            listar("vendedor=sistema").andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(suya)));
            // Con el ticket en la mano (número exacto) encuentra la venta de otro, para reimprimirla o cambiarla.
            listar("busqueda=" + facturaMixta).andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(facturaMixta)));
            // Sin caja abierta en ese local, recorrer los números no sirve para ver ventas ajenas.
            cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("139.90"), null));
            listar("busqueda=" + facturaMixta).andExpect(jsonPath("$.content", hasSize(0)));
            listar("busqueda=" + suya).andExpect(jsonPath("$.content[*].numero", containsInAnyOrder(suya)));
        } finally {
            SecurityContextHolder.clearContext();
        }
        // El administrador (o un proceso sin sesión) ve todas.
        listar("").andExpect(jsonPath("$.content", hasSize(9)));
    }

    @Test
    void rechazaFiltrosInvalidosConMensajeClaro() throws Exception {
        listar("metodoPago=BITCOIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El valor 'BITCOIN' no es válido para metodoPago"));
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        listar("desde=" + hoy + "&hasta=" + hoy.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha desde (" + hoy + ") es posterior a la fecha hasta ("
                        + hoy.minusDays(1) + ")"));
    }

    private ResultActions listar(String filtros) throws Exception {
        return consultar("/api/ventas?size=50&establecimientoId=" + tienda + "&" + filtros);
    }

    private ResultActions consultar(String url) throws Exception {
        return mvc.perform(get(url));
    }

    private static PagoRequest pago(MetodoPago metodo, String monto) {
        return new PagoRequest(metodo, new BigDecimal(monto), null, "OP-123");
    }
}
