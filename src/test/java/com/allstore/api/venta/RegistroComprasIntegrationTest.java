package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.compra.dto.AnularCompraRequest;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import com.allstore.api.compra.service.CompraService;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.contabilidad.dto.RegistroComprasResponse;
import com.allstore.api.contabilidad.service.ArchivoRvie;
import com.allstore.api.contabilidad.service.RegistroComprasService;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.service.EmpresaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RCE: facturas con crédito fiscal, boletas sin él, anuladas y recibos por honorarios fuera. */
class RegistroComprasIntegrationTest extends TiendaDePrueba {

    @Autowired private RegistroComprasService registroComprasService;
    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private EmpresaService empresaService;

    private YearMonth periodo;
    private LocalDate dia;

    @BeforeEach
    void compras() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Jeans Prueba",
                    "Jr. Prueba 1", "150115", null, "ventas@prueba.pe", null));
        }
        // Un periodo pasado y propio de esta prueba: así no se mezcla con compras de otras pruebas.
        periodo = YearMonth.of(2025, 3);
        dia = periodo.atDay(10);
        Long proveedor = proveedorService.crear(new ProveedorRequest(TipoDocumentoIdentidad.RUC, "20100047218",
                "TEXTIL PRUEBA SAC", null, null, null, null)).id();
        compra(proveedor, TipoDocumentoCompra.FACTURA, "F001", "1", "100.00");
        compra(proveedor, TipoDocumentoCompra.BOLETA, "B001", "2", "50.00");
        compra(proveedor, TipoDocumentoCompra.RECIBO_HONORARIOS, "E001", "3", "300.00");
        Long anulada = compra(proveedor, TipoDocumentoCompra.FACTURA, "F001", "4", "80.00");
        compraService.anular(anulada, new AnularCompraRequest("Error"));
    }

    @Test
    void elRegistroSumaElCreditoFiscalDeLasFacturas() {
        RegistroComprasResponse rce = registroComprasService.generar(periodo);
        assertThat(rce.periodo()).isEqualTo("202503");
        assertThat(rce.documentos()).isEqualTo(2);
        assertThat(rce.baseGravada()).isEqualByComparingTo("100.00");
        assertThat(rce.creditoFiscal()).isEqualByComparingTo("18.00");
        assertThat(rce.noGravado()).isEqualByComparingTo("50.00");
        assertThat(rce.total()).isEqualByComparingTo("168.00");
        assertThat(rce.advertencias()).anyMatch(a -> a.contains("no van al Registro de Compras"))
                .anyMatch(a -> a.startsWith("1 boleta(s) o ticket(s) no vienen en la propuesta de SUNAT"))
                .anyMatch(a -> a.contains("ya está disponible en el SIRE (desde el 08/04/2025)"));
        assertThat(rce.registros()).extracting(RegistroComprasResponse.Registro::propuestoPorSunat).containsExactly(true, false);
    }

    @Test
    void elArchivoDelSireTieneUnaLineaPorDocumento() {
        ArchivoRvie.Archivo archivo = registroComprasService.archivoSire(periodo, false);
        assertThat(archivo.nombre()).isEqualTo("LE2000000000120250300080400021112.txt");
        String[] lineas = new String(archivo.contenido(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(lineas).hasSize(2);
        assertThat(lineas[0].split("\\|", -1)).hasSize(36)
                .satisfies(c -> {
                    assertThat(c[6]).isEqualTo("01");
                    assertThat(c[14]).isEqualTo("100.00");
                    assertThat(c[15]).isEqualTo("18.00");
                    assertThat(c[24]).isEqualTo("118.00");
                });
    }

    private Long compra(Long proveedor, TipoDocumentoCompra tipo, String serie, String numero, String costo) {
        return compraService.registrar(new CompraRequest(proveedor, tienda, tipo, serie, numero, dia, null, null, false,
                List.of(new CompraRequest.Item(null, null, "Servicio de prueba", BigDecimal.ONE, new BigDecimal(costo), null)))).id();
    }
}
