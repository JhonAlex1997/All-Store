package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Registro;
import com.allstore.api.contabilidad.dto.RegistroVentasResponse.Situacion;
import com.allstore.api.contabilidad.service.RegistroVentasService;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Registro de ventas del mes armado desde los comprobantes emitidos, con sus reglas del Anexo 3. */
class RegistroVentasIntegrationTest extends TiendaDePrueba {

    @Autowired private RegistroVentasService registroVentasService;
    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EmpresaService empresaService;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    private final YearMonth mes = YearMonth.now(JpaAuditingConfig.ZONA_LIMA);

    @BeforeEach
    void preparar() throws Exception {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", null,
                    "Jr. Prueba 1", "150115", null, null, null));
        }
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(
                new ServicioEnvioSunat.Respuesta(true, "0", "Aceptada", List.of(), new byte[]{1}));
    }

    @Test
    void registraLasVentasDelMesConSusReglasYAdvierteLoPendiente() {
        VentaResponse factura = aceptada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 2, null)), List.of(efectivo("179.80", null))));
        VentaResponse nota = aceptada(notaCreditoService.emitir(factura.id(), new NotaCreditoRequest(serieNcFactura,
                MotivoNotaCredito.DEVOLUCION_POR_ITEM, "Cambio de talla", MetodoPago.EFECTIVO, null,
                List.of(new ItemDevolucionRequest(jeanA, 1)))));
        VentaResponse pendiente = boletaAClientesVarios();
        VentaResponse anulada = aceptada(boletaAClientesVarios());
        anulacionService.anular(anulada.id(), new AnularVentaRequest("Error en la venta"));
        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)));

        RegistroVentasResponse registro = registroVentasService.generar(mes);

        assertThat(registro.registros()).extracting(Registro::tipoComprobante).as("la nota de venta no va al registro")
                .containsExactlyInAnyOrder("01", "07", "03", "03");
        Registro deNota = registro(registro, nota);
        assertThat(deNota.total()).isEqualByComparingTo("-89.90");
        assertThat(deNota.igv()).isEqualByComparingTo("-13.71");
        assertThat(deNota.modifica().serie()).isEqualTo("F991");
        assertThat(deNota.modifica().numero()).isEqualTo(1);
        Registro deAnulada = registro(registro, anulada);
        assertThat(deAnulada.situacion()).isEqualTo(Situacion.ANULADO);
        assertThat(deAnulada.total()).isEqualByComparingTo("0.00");
        Registro dePendiente = registro(registro, pendiente);
        assertThat(dePendiente.situacion()).isEqualTo(Situacion.PENDIENTE_SUNAT);
        assertThat(dePendiente.clienteTipoDocumento()).as("boleta menor a S/ 700 sin identificar").isNull();

        // 179.80 - 89.90 + 89.90: la anulada no suma.
        assertThat(registro.totales().total()).isEqualByComparingTo("179.80");
        assertThat(registro.totales().baseGravada()).isEqualByComparingTo("152.37");
        assertThat(registro.totales().igv()).isEqualByComparingTo("27.43");
        assertThat(registro.resumenPorTipo()).extracting(RegistroVentasResponse.Resumen::tipoComprobante)
                .containsExactly("01", "03", "07");
        assertThat(registro.advertencias())
                .anySatisfy(a -> assertThat(a).contains("aún no aceptado(s) por SUNAT: " + pendiente.numero()))
                .anySatisfy(a -> assertThat(a).contains(anulada.numero() + " está anulado pero SUNAT aún no acepta su baja"))
                .anySatisfy(a -> assertThat(a).contains("1 nota(s) de venta por S/ 89.90"));
    }

    @Test
    void elArchivoParaElSireTieneUnaLineaPorComprobante() {
        aceptada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null))));
        boletaAClientesVarios();

        var archivo = registroVentasService.archivoSire(mes, false);
        // Pasa la revisión con las reglas de la estructura oficial del SIRE.
        assertThat(registroVentasService.validar(mes).inconsistencias()).isEmpty();

        assertThat(archivo.nombre()).isEqualTo("LE20000000001" + mes.toString().replace("-", "") + "0014040002" + "1112.txt");
        String[] lineas = new String(archivo.contenido(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(lineas).hasSize(2).allSatisfy(l -> assertThat(l.split("\\|", -1)).hasSize(33));
    }

    @Test
    void noSeGeneraUnPeriodoQueAunNoEmpieza() {
        assertThatThrownBy(() -> registroVentasService.generar(mes.plusMonths(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aún no empieza");
    }

    // ---------- Ayudas ----------

    private VentaResponse boletaAClientesVarios() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)));
    }

    private VentaResponse aceptada(VentaResponse comprobante) {
        envioSunatService.enviarAhora(comprobante.id(), false);
        return ventaService.obtenerDetalle(comprobante.id());
    }

    private static Registro registro(RegistroVentasResponse registro, VentaResponse comprobante) {
        return registro.registros().stream().filter(r -> r.comprobanteId().equals(comprobante.id())).findFirst().orElseThrow();
    }
}
