package com.allstore.api.venta;

import com.allstore.api.bitacora.dto.RegistroBitacoraResponse;
import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.common.exception.AutenticacionException;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.inventario.dto.MovimientoManualRequest;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.dto.ProductoUpdateRequest;
import com.allstore.api.seguridad.dto.LoginRequest;
import com.allstore.api.seguridad.dto.UsuarioCreateRequest;
import com.allstore.api.seguridad.dto.UsuarioUpdateRequest;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.service.AuthService;
import com.allstore.api.seguridad.service.UsuarioService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La bitácora registra las acciones sensibles, con quién, qué y el detalle; y no se puede alterar. */
class BitacoraIntegrationTest extends TiendaDePrueba {

    @Autowired private Bitacora bitacora;
    @Autowired private UsuarioService usuarioService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anularUnComprobanteQuedaConSuMotivo() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        anulacionService.anular(nota.id(), new AnularVentaRequest("El cliente se arrepintió"));

        assertThat(registros(AccionBitacora.COMPROBANTE_ANULADO, nota.id())).singleElement().satisfies(r -> {
            assertThat(r.usuario()).isEqualTo("sistema");
            assertThat(r.descripcion()).isEqualTo("Anuló nota de venta " + nota.numero() + " por S/ 89.90");
            assertThat(r.detalle()).isEqualTo("Motivo: El cliente se arrepintió");
            assertThat(r.accionDescripcion()).isEqualTo("Anuló un comprobante");
        });
    }

    @Test
    void unaVentaConDescuentoOPorMayorManualQuedaRegistradaYUnaNormalNo() {
        Long modelo = productoDe(jeanA);
        productoService.actualizar(modelo, new ProductoUpdateRequest("Jean prueba", null, 1L, new BigDecimal("89.90"),
                new BigDecimal("80.00"), true));
        VentaResponse normal = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        VentaResponse conDescuento = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null,
                List.of(item(jeanA, 1, "10.00"), item(jeanB, 1, null)), List.of(efectivo("169.80", null)));
        VentaResponse porMayor = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, null,
                List.of(item(jeanB, 1, null)), List.of(efectivo("80.00", null)), true, null, null));

        assertThat(bitacora.consultar(null, null, null, null, "COMPROBANTE", normal.id(), PageRequest.of(0, 10)).content()).isEmpty();
        assertThat(registros(AccionBitacora.DESCUENTO_EN_VENTA, conDescuento.id())).singleElement().satisfies(r -> {
            assertThat(r.descripcion()).contains("con S/ 10.00 de descuento", "total S/ 169.80");
            assertThat(r.detalle()).matches("PRUEBA-VTA-\\S+ x1: -S/ 10.00");
        });
        assertThat(porMayor.porMayorManual()).isTrue();
        assertThat(registros(AccionBitacora.POR_MAYOR_MANUAL, porMayor.id())).singleElement()
                .satisfies(r -> assertThat(r.descripcion()).contains("con 1 prenda (total S/ 80.00)"));
    }

    @Test
    void cambiarElPrecioGuardaElAntesYElDespuesYEditarSinCambiarloNo() {
        ProductoDetalleResponse jean = productoService.obtenerDetalle(productoDe(jeanA));
        productoService.actualizar(jean.id(), new ProductoUpdateRequest(jean.nombre(), null, 1L, jean.precioVenta(), null, true));
        assertThat(registros(AccionBitacora.PRECIO_CAMBIADO, jean.id())).isEmpty();

        productoService.actualizar(jean.id(), new ProductoUpdateRequest(jean.nombre(), null, 1L, new BigDecimal("79.90"),
                new BigDecimal("70.00"), true));
        assertThat(registros(AccionBitacora.PRECIO_CAMBIADO, jean.id())).singleElement().satisfies(r -> {
            assertThat(r.entidad()).isEqualTo("PRODUCTO");
            assertThat(r.detalle()).isEqualTo("Precio: S/ 89.90 → S/ 79.90; Por mayor: sin precio → S/ 70.00");
        });
    }

    @Test
    void ajusteDeStockYCajaConFaltante() {
        inventarioService.registrarManual(new MovimientoManualRequest(tienda, MotivoMovimiento.AJUSTE_SALIDA,
                "Prendas dañadas", List.of(new ItemCantidadRequest(jeanA, 2))));
        assertThat(registros(AccionBitacora.AJUSTE_INVENTARIO, tienda)).singleElement().satisfies(r -> {
            assertThat(r.descripcion()).isEqualTo("AJUSTE_SALIDA en Tienda prueba: 2 prendas");
            assertThat(r.detalle()).endsWith(" -2. Prendas dañadas");
        });

        vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanB, 1, null)), List.of(efectivo("89.90", null)));
        // Esperado: 100 de fondo + 89.90; contado 180: faltan 9.90.
        var caja = cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("180.00"), "Faltó sencillo"));
        assertThat(registros(AccionBitacora.CAJA_CON_DIFERENCIA, caja.id())).singleElement().satisfies(r -> {
            assertThat(r.descripcion()).isEqualTo("Cerró la caja con faltante de S/ 9.90");
            assertThat(r.detalle()).isEqualTo("Esperado S/ 189.90, contado S/ 180.00. Faltó sencillo");
        });
    }

    @Test
    void cambiosDeAccesoDeUsuariosYElBloqueoPorIntentos() {
        var usuario = usuarioService.crear(new UsuarioCreateRequest("b_cajero", "Cajero Bitácora", Rol.CAJERO, "Temporal2026x"));
        usuarioService.actualizar(usuario.id(), new UsuarioUpdateRequest("Cajero Bitácora", Rol.ALMACENERO, false), "admin");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("b_cajero", "Incorrecta123")))
                    .isInstanceOf(AutenticacionException.class);
        }
        usuarioService.desbloquear(usuario.id());

        List<RegistroBitacoraResponse> historia = bitacora.consultar(null, null, null, null, "USUARIO", usuario.id(),
                PageRequest.of(0, 10)).content();
        assertThat(historia).extracting(RegistroBitacoraResponse::accion).containsExactlyInAnyOrder(
                AccionBitacora.USUARIO_CREADO, AccionBitacora.USUARIO_MODIFICADO, AccionBitacora.USUARIO_BLOQUEADO,
                AccionBitacora.USUARIO_DESBLOQUEADO);
        assertThat(historia).filteredOn(r -> r.accion() == AccionBitacora.USUARIO_MODIFICADO).singleElement()
                .satisfies(r -> assertThat(r.descripcion()).isEqualTo("Cambió al usuario b_cajero: rol CAJERO → ALMACENERO, desactivado"));
        assertThat(historia).filteredOn(r -> r.accion() == AccionBitacora.USUARIO_BLOQUEADO).singleElement()
                .satisfies(r -> assertThat(r.usuario()).isEqualTo(Bitacora.ANONIMO));
    }

    @Test
    void laBitacoraNoSeModificaNiSeBorra() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        anulacionService.anular(nota.id(), new AnularVentaRequest("Prueba"));
        Long id = registros(AccionBitacora.COMPROBANTE_ANULADO, nota.id()).getFirst().id();

        assertThatThrownBy(() -> jdbc.update("update bitacora set usuario = 'otro' where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("no se modifica ni se borra");
    }

    private List<RegistroBitacoraResponse> registros(AccionBitacora accion, Long entidadId) {
        return bitacora.consultar(null, null, null, List.of(accion), null, entidadId, PageRequest.of(0, 20)).content();
    }

    private Long productoDe(Long varianteId) {
        return jdbc.queryForObject("select producto_id from producto_variante where id = ?", Long.class, varianteId);
    }
}
