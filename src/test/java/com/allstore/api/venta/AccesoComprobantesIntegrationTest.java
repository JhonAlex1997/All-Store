package com.allstore.api.venta;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.AccesoDenegadoException;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.service.EntregaService;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un cajero solo ve y usa (detalle, PDF, enlaces, correo, notas) sus comprobantes y los del local
 * donde tiene la caja abierta; y no puede usar el correo de la tienda para mandar comprobantes en
 * masa ni meter enlaces en los datos que llegan al cliente.
 */
class AccesoComprobantesIntegrationTest extends TiendaDePrueba {

    @Autowired private EntregaService entregaService;
    @Autowired private EmpresaService empresaService;
    @Autowired private Validator validator;

    /** Venta del administrador (en las pruebas, el usuario "sistema"). */
    private VentaResponse ajena;

    @BeforeEach
    void ventaDeOtro() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Jeans Prueba",
                    "Jr. Prueba 1", "150115", null, "ventas@prueba.pe", null));
        }
        ajena = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }

    @AfterEach
    void salir() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinCajaAbiertaEnEseLocalElCajeroNoVeNiUsaComprobantesAjenos() {
        comoCajero();

        assertThatThrownBy(() -> ventaService.obtenerDetalle(ajena.id())).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> entregaService.entrega(ajena.id())).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> entregaService.enviarCorreo(ajena.id(), "atacante@externo.com"))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> notaCreditoService.emitir(ajena.id(), devolucion()))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void conCajaAbiertaEnElLocalAtiendeLaVentaDeUnCompanero() {
        comoCajero();
        cajaService.abrir(new AbrirCajaRequest(tienda, new BigDecimal("50.00")));

        assertThat(ventaService.obtenerDetalle(ajena.id()).numero()).isEqualTo(ajena.numero());
        assertThatCode(() -> notaCreditoService.emitir(ajena.id(), devolucion())).doesNotThrowAnyException();

        // Al cerrar su caja deja de verla.
        cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("50.00"), null));
        assertThatThrownBy(() -> ventaService.obtenerDetalle(ajena.id())).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void elCajeroSiempreVeLasSuyas() {
        comoCajero();
        cajaService.abrir(new AbrirCajaRequest(tienda, new BigDecimal("50.00")));
        VentaResponse suya = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanB, 1, null)),
                List.of(efectivo("89.90", null)));
        cajaService.cerrar(new CerrarCajaRequest(new BigDecimal("139.90"), null));

        assertThat(ventaService.obtenerDetalle(suya.id()).numero()).isEqualTo(suya.numero());
    }

    @Test
    void unComprobanteNoSeMandaPorCorreoMasDeCincoVecesAlDia() {
        for (int i = 0; i < 5; i++) {
            entregaService.enviarCorreo(ajena.id(), "cliente" + i + "@correo.pe");
        }
        assertThatThrownBy(() -> entregaService.enviarCorreo(ajena.id(), "otro@correo.pe"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("es el máximo");
    }

    @Test
    void losDatosQueLleganAlClienteNoAdmitenEnlaces() {
        ClienteRequest phishing = new ClienteRequest(TipoDocumentoIdentidad.DNI, "40404040",
                "Juan. URGENTE: confirma tu pago en http://pago-tienda.xyz", null, null, null, null, true);

        assertThat(validator.validate(phishing)).singleElement()
                .satisfies(v -> assertThat(v.getMessage()).contains("No se permiten enlaces"));
    }

    private void comoCajero() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("cajero1", null, "ROLE_CAJERO"));
    }

    private NotaCreditoRequest devolucion() {
        return new NotaCreditoRequest(serieNcBoleta, MotivoNotaCredito.DEVOLUCION_POR_ITEM, "Cambio de talla",
                MetodoPago.EFECTIVO, null, List.of(new ItemDevolucionRequest(jeanA, 1)));
    }
}
