package com.allstore.api.venta;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.version.EdicionConcurrenteException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.producto.dto.VarianteUpdateRequest;
import com.allstore.api.proforma.dto.EnviarProformaRequest;
import com.allstore.api.proforma.dto.ProformaRequest;
import com.allstore.api.proforma.dto.ProformaResponse;
import com.allstore.api.proforma.entity.EstadoProforma;
import com.allstore.api.proforma.entity.Proforma;
import com.allstore.api.proforma.repository.ProformaRepository;
import com.allstore.api.proforma.service.ProformaService;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proformas: se cotizan como una venta, no mueven stock, se cobran con los precios cotizados mientras
 * estén vigentes (7 días) y solo una vez.
 */
class ProformaIntegrationTest extends TiendaDePrueba {

    @Autowired private ProformaService proformaService;
    @Autowired private ProformaRepository proformaRepository;
    @Autowired private EmpresaService empresaService;

    private final LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);

    @Test
    void seCotizaComoUnaVentaYNoMueveStock() {
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 2, null), item(jeanB, 1, null));

        assertThat(p.numero()).matches("PR01-\\d{8}");
        assertThat(p.estado()).isEqualTo(EstadoProforma.PENDIENTE);
        assertThat(p.vigenteHasta()).isEqualTo(hoy.plusDays(7));
        assertThat(p.diasVigencia()).isEqualTo(7);
        assertThat(p.vencida()).isFalse();
        assertThat(p.prendas()).isEqualTo(3);
        assertThat(p.cliente().nombre()).isEqualTo("Ana Prueba");
        assertThat(p.lineas()).extracting(ProformaResponse.Linea::stock).containsExactly(5, 10);
        // Tres prendas: llega al por mayor, pero este jean no tiene precio por mayor; total igual que una venta.
        assertThat(p.total()).isEqualByComparingTo("269.70");
        assertThat(stock(jeanA, tienda)).as("la proforma no reserva ni descuenta stock").isEqualTo(5);
    }

    @Test
    void alCobrarlaSeRespetaElPrecioCotizadoAunqueHayaSubido() {
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 2, null));
        productoService.actualizarVariante(jeanA, new VarianteUpdateRequest(new BigDecimal("99.90"), null, true));

        VentaResponse venta = proformaService.vender(p.id(), venta(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                "179.80", item(jeanA, 2, null)), UUID.randomUUID().toString());

        assertThat(venta.total()).isEqualByComparingTo("179.80");
        assertThat(venta.detalles().getFirst().precioUnitario()).isEqualByComparingTo("89.90");
        assertThat(stock(jeanA, tienda)).as("la venta sí descuenta stock").isEqualTo(3);
        ProformaResponse vendida = proformaService.obtener(p.id());
        assertThat(vendida.estado()).isEqualTo(EstadoProforma.VENDIDA);
        assertThat(vendida.comprobante()).isEqualTo(venta.numero());
        assertThat(vendida.comprobanteId()).isEqualTo(venta.id());
    }

    @Test
    void noSeCobraDosVecesPeroUnReintentoDevuelveLaMismaVenta() {
        ProformaResponse p = cotizar(null, item(jeanB, 1, null));
        String clave = UUID.randomUUID().toString();
        VentaRequest request = venta(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, "89.90", item(jeanB, 1, null));

        VentaResponse primera = proformaService.vender(p.id(), request, clave);
        VentaResponse reintento = proformaService.vender(p.id(), request, clave);

        assertThat(reintento.id()).isEqualTo(primera.id());
        assertThat(stock(jeanB, tienda)).isEqualTo(9);
        assertThatThrownBy(() -> proformaService.vender(p.id(), request, UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se cobró con " + primera.numero());
        assertThatThrownBy(() -> proformaService.actualizar(p.id(), pedido(null, item(jeanB, 2, null)), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya no se puede cambiar");
    }

    @Test
    void vencidaNoSeCobraHastaRenovarlaYAlRenovarTomaLosPreciosDeHoy() {
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 1, null));
        Proforma entidad = proformaRepository.findById(p.id()).orElseThrow();
        entidad.setVigenteHasta(hoy.minusDays(1));
        proformaRepository.flush();
        productoService.actualizarVariante(jeanA, new VarianteUpdateRequest(new BigDecimal("99.90"), null, true));

        ProformaResponse vencida = proformaService.obtener(p.id());
        assertThat(vencida.vencida()).isTrue();
        assertThat(vencida.diasVigencia()).isNull();
        assertThatThrownBy(() -> proformaService.vender(p.id(), venta(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                "89.90", item(jeanA, 1, null)), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("renuévala");

        ProformaResponse renovada = proformaService.renovar(p.id(), vencida.version());

        assertThat(renovada.vencida()).isFalse();
        assertThat(renovada.vigenteHasta()).isEqualTo(hoy.plusDays(7));
        assertThat(renovada.total()).isEqualByComparingTo("99.90");
        assertThat(renovada.numero()).as("conserva su número").isEqualTo(p.numero());
        VentaResponse venta = proformaService.vender(p.id(), venta(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                "99.90", item(jeanA, 1, null)), UUID.randomUUID().toString());
        assertThat(venta.total()).isEqualByComparingTo("99.90");
    }

    @Test
    void laVentaDebeLlevarLasMismasPrendasDeLaProforma() {
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 2, null));

        assertThatThrownBy(() -> proformaService.vender(p.id(), venta(TipoComprobante.BOLETA, serieBoleta, clienteDni,
                "89.90", item(jeanA, 1, null)), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("mismas prendas y cantidades");
        assertThat(proformaService.obtener(p.id()).estado()).isEqualTo(EstadoProforma.PENDIENTE);
    }

    @Test
    void editarRecalculaRenuevaYAvisaSiOtraPersonaLaCambio() {
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 1, null));

        ProformaResponse editada = proformaService.actualizar(p.id(), pedido(clienteRuc, item(jeanA, 1, null),
                item(jeanB, 2, null)), p.version());

        assertThat(editada.numero()).isEqualTo(p.numero());
        assertThat(editada.prendas()).isEqualTo(3);
        assertThat(editada.cliente().nombre()).isEqualTo("EMPRESA PRUEBA SAC");
        assertThat(editada.version()).isGreaterThan(p.version());
        assertThatThrownBy(() -> proformaService.actualizar(p.id(), pedido(clienteDni, item(jeanA, 1, null)), p.version()))
                .isInstanceOf(EdicionConcurrenteException.class);
    }

    @Test
    void anuladaNoSeCobraNiSeEnvia() {
        ProformaResponse p = cotizar(null, item(jeanA, 1, null));

        proformaService.anular(p.id(), p.version());

        assertThatThrownBy(() -> proformaService.vender(p.id(), venta(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                "89.90", item(jeanA, 1, null)), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("anulada");
        assertThatThrownBy(() -> proformaService.enviar(p.id(), null, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("anulada");
    }

    @Test
    void seImprimeEnA4YTicketYSeComparteConElEnlaceDeWhatsapp() {
        empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Tienda Prueba",
                "Jr. Prueba 1", "150115", null, null, null));
        ProformaResponse p = cotizar(clienteDni, item(jeanA, 1, null));

        assertThat(proformaService.pdf(p.id(), FormatoImpresion.A4).contenido()).startsWith("%PDF".getBytes());
        assertThat(proformaService.pdf(p.id(), FormatoImpresion.TICKET).nombreArchivo()).isEqualTo(p.numero() + "-ticket.pdf");

        var envio = proformaService.enviar(p.id(), new EnviarProformaRequest("987 654 321", null), false);
        assertThat(envio.enlaceWhatsapp()).startsWith("https://wa.me/51987654321?text=")
                .contains(p.numero().replace("-", "-")).contains("89.90");
        assertThat(envio.enlacePdf()).contains("/api/publico/proformas/").endsWith("/pdf");
        assertThat(envio.correo()).isNull();
        assertThatThrownBy(() -> proformaService.enviar(p.id(), new EnviarProformaRequest("123", null), false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no es válido");
    }

    @Test
    void seBuscaPorNumeroClienteYVigencia() {
        ProformaResponse a = cotizar(clienteDni, item(jeanA, 1, null));
        ProformaResponse b = cotizar(clienteRuc, item(jeanB, 1, null));
        Proforma vencida = proformaRepository.findById(b.id()).orElseThrow();
        vencida.setVigenteHasta(hoy.minusDays(2));
        proformaRepository.flush();

        assertThat(ids(proformaService.listar(tienda, null, null, a.numero(), PageRequest.of(0, 10)).content()))
                .containsExactly(a.id());
        assertThat(ids(proformaService.listar(tienda, null, null, "empresa prueba", PageRequest.of(0, 10)).content()))
                .containsExactly(b.id());
        assertThat(ids(proformaService.listar(tienda, null, "VENCIDA", null, PageRequest.of(0, 10)).content()))
                .containsExactly(b.id());
        assertThat(ids(proformaService.listar(tienda, null, "VIGENTE", null, PageRequest.of(0, 10)).content()))
                .containsExactly(a.id());
    }

    private ProformaResponse cotizar(Long clienteId, VentaItemRequest... items) {
        return proformaService.crear(pedido(clienteId, items));
    }

    private ProformaRequest pedido(Long clienteId, VentaItemRequest... items) {
        return new ProformaRequest(tienda, clienteId, null, List.of(items), null);
    }

    private static VentaRequest venta(TipoComprobante tipo, Long serieId, Long clienteId, String total,
                                      VentaItemRequest... items) {
        return new VentaRequest(tipo, serieId, clienteId, null, List.of(items), List.of(efectivo(total, null)),
                null, null, null);
    }

    private static List<Long> ids(List<ProformaResponse> proformas) {
        return proformas.stream().map(ProformaResponse::id).toList();
    }
}
