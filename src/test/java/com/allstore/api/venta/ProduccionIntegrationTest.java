package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.insumo.dto.AjusteInsumoRequest;
import com.allstore.api.insumo.dto.InsumoRequest;
import com.allstore.api.insumo.dto.StockInsumoResponse;
import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.service.InsumoService;
import com.allstore.api.produccion.dto.AnularOrdenRequest;
import com.allstore.api.produccion.dto.ConsumoRequest;
import com.allstore.api.produccion.dto.EtapaRequest;
import com.allstore.api.produccion.dto.OrdenRequest;
import com.allstore.api.produccion.dto.OrdenResponse;
import com.allstore.api.produccion.dto.TerminarOrdenRequest;
import com.allstore.api.produccion.entity.EstadoOrden;
import com.allstore.api.produccion.entity.Etapa;
import com.allstore.api.produccion.service.ProduccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Producción: etapas en orden, merma, consumo de insumos, ingreso al stock con su costo y anulación. */
class ProduccionIntegrationTest extends TiendaDePrueba {

    @Autowired private ProduccionService produccionService;
    @Autowired private InsumoService insumoService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private JdbcTemplate jdbc;

    private Long modelo;
    private Long tela;
    private Long lavanderia;

    @BeforeEach
    void datos() {
        modelo = jdbc.queryForObject("select producto_id from producto_variante where id = ?", Long.class, jeanA);
        tela = insumoService.crear(new InsumoRequest("DENIM-PRUEBA", "Denim de prueba", "MTR", new BigDecimal("12.0000"), null)).id();
        insumoService.ajustar(new AjusteInsumoRequest(almacen, MotivoInsumo.INVENTARIO_INICIAL, null,
                List.of(new AjusteInsumoRequest.Item(tela, new BigDecimal("100")))));
        lavanderia = proveedorService.crear(new ProveedorRequest(TipoDocumentoIdentidad.RUC, "20100047218", "LAVANDERIA PRUEBA SAC",
                null, null, null, null)).id();
    }

    @Test
    void ordenCompletaHastaElStockConSuCosto() {
        OrdenResponse orden = produccionService.crear(new OrdenRequest(modelo, almacen, tienda, "Lote noviembre", List.of(
                new OrdenRequest.Cantidad(jeanA, 10), new OrdenRequest.Cantidad(jeanB, 10))));
        assertThat(orden.etapaActual()).isEqualTo(Etapa.CORTE);
        produccionService.consumir(orden.id(), new ConsumoRequest(List.of(new ConsumoRequest.Item(tela, new BigDecimal("25")))));
        produccionService.devolver(orden.id(), new ConsumoRequest(List.of(new ConsumoRequest.Item(tela, new BigDecimal("5")))));

        // No se puede saltar etapas.
        assertThatThrownBy(() -> produccionService.iniciarEtapa(orden.id(), Etapa.CONFECCION, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Primero termina la etapa de corte");

        etapa(orden.id(), Etapa.CORTE, null, 20, null);
        etapa(orden.id(), Etapa.CONFECCION, null, 19, "190.00");      // 1 de merma
        etapa(orden.id(), Etapa.LAVADO, lavanderia, 19, "95.00");
        assertThatThrownBy(() -> etapa(orden.id(), Etapa.ACABADO, null, 20, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no pueden salir más");
        produccionService.terminarEtapa(orden.id(), Etapa.ACABADO, new EtapaRequest(null, 19, null, null));

        OrdenResponse terminada = produccionService.terminar(orden.id(), new TerminarOrdenRequest(List.of(
                new TerminarOrdenRequest.Cantidad(jeanA, 10), new TerminarOrdenRequest.Cantidad(jeanB, 9)), true));
        assertThat(terminada.estado()).isEqualTo(EstadoOrden.TERMINADA);
        // Insumos: 20 m x 12 = 240; servicios 190 + 95 = 285; (240 + 285) / 19 = 27.6316.
        assertThat(terminada.costoInsumos()).isEqualByComparingTo("240.00");
        assertThat(terminada.costoServicios()).isEqualByComparingTo("285.00");
        assertThat(terminada.costoUnitario()).isEqualByComparingTo("27.6316");
        assertThat(stock(jeanA, tienda)).isEqualTo(15);
        assertThat(stock(jeanB, tienda)).isEqualTo(19);
        assertThat(insumoService.stock(almacen)).filteredOn(s -> s.insumoId().equals(tela))
                .extracting(StockInsumoResponse::cantidad).singleElement()
                .satisfies(c -> assertThat(c).isEqualByComparingTo("80"));
        assertThat(productoService.obtenerVariantes(List.of(jeanA)).get(jeanA).getCostoEfectivo()).isEqualByComparingTo("27.63");
        assertThat(terminada.etapas()).filteredOn(e -> e.etapa() == Etapa.LAVADO).singleElement()
                .satisfies(e -> assertThat(e.taller()).isEqualTo("LAVANDERIA PRUEBA SAC"));
    }

    @Test
    void anularEnProcesoDevuelveLosInsumosYTerminadaSacaLasPrendas() {
        OrdenResponse orden = produccionService.crear(new OrdenRequest(modelo, almacen, tienda, null,
                List.of(new OrdenRequest.Cantidad(jeanA, 5))));
        produccionService.consumir(orden.id(), new ConsumoRequest(List.of(new ConsumoRequest.Item(tela, new BigDecimal("8")))));
        produccionService.anular(orden.id(), new AnularOrdenRequest("Se canceló el pedido"));
        assertThat(insumoService.stock(almacen)).filteredOn(s -> s.insumoId().equals(tela))
                .extracting(StockInsumoResponse::cantidad).singleElement()
                .satisfies(c -> assertThat(c).isEqualByComparingTo("100"));

        OrdenResponse otra = produccionService.crear(new OrdenRequest(modelo, almacen, tienda, null,
                List.of(new OrdenRequest.Cantidad(jeanA, 3))));
        for (Etapa e : Etapa.values()) {
            etapa(otra.id(), e, null, 3, null);
        }
        produccionService.terminar(otra.id(), new TerminarOrdenRequest(List.of(new TerminarOrdenRequest.Cantidad(jeanA, 3)), false));
        assertThat(stock(jeanA, tienda)).isEqualTo(8);
        produccionService.anular(otra.id(), new AnularOrdenRequest("Error de registro"));
        assertThat(stock(jeanA, tienda)).isEqualTo(5);
    }

    private void etapa(Long orden, Etapa etapa, Long taller, int unidades, String costo) {
        produccionService.iniciarEtapa(orden, etapa, new EtapaRequest(taller, null, null, null));
        produccionService.terminarEtapa(orden, etapa, new EtapaRequest(null, unidades, costo == null ? null : new BigDecimal(costo), null));
    }
}
