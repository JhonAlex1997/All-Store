package com.allstore.api.venta;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.idempotencia.SolicitudProcesadaRepository;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.CompraResponse;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.entity.TipoDocumentoCompra;
import com.allstore.api.compra.service.CompraService;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.insumo.dto.AjusteInsumoRequest;
import com.allstore.api.insumo.dto.InsumoRequest;
import com.allstore.api.insumo.dto.MovimientoInsumoResponse;
import com.allstore.api.insumo.dto.StockInsumoResponse;
import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.service.InsumoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Protección general contra doble clic: con la misma Idempotency-Key la operación se hace una vez. */
class UnaVezIntegrationTest extends TiendaDePrueba {

    @Autowired private UnaVez unaVez;
    @Autowired private InsumoService insumoService;
    @Autowired private CompraService compraService;
    @Autowired private ProveedorService proveedorService;
    @Autowired private SolicitudProcesadaRepository solicitudRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long tela;

    @BeforeEach
    void datos() {
        tela = insumoService.crear(new InsumoRequest("TELA-UNAVEZ", "Tela de prueba", "MTR", null, null)).id();
    }

    @AfterEach
    void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elDobleClicEnUnAjusteDeInsumosLoHaceUnaSolaVez() {
        AjusteInsumoRequest ajuste = new AjusteInsumoRequest(almacen, MotivoInsumo.INVENTARIO_INICIAL, null,
                List.of(new AjusteInsumoRequest.Item(tela, new BigDecimal("10"))));
        List<MovimientoInsumoResponse> primera = ajustar("clave-ajuste-001", ajuste);
        List<MovimientoInsumoResponse> segunda = ajustar("clave-ajuste-001", ajuste);

        assertThat(segunda).extracting(MovimientoInsumoResponse::id).isEqualTo(primera.stream().map(MovimientoInsumoResponse::id).toList());
        assertThat(stockTela()).isEqualByComparingTo("10");
        // Sin la clave, cada solicitud se ejecuta (como siempre).
        ajustar(null, ajuste);
        assertThat(stockTela()).isEqualByComparingTo("20");
    }

    @Test
    void unaCompraReintentadaDevuelveLaMismaCompra() {
        Long proveedor = proveedorService.crear(new ProveedorRequest(TipoDocumentoIdentidad.RUC, "20100047218", "TEXTIL SAC",
                null, null, null, null)).id();
        CompraRequest compra = new CompraRequest(proveedor, tienda, TipoDocumentoCompra.FACTURA, "F001", "55", LocalDate.now(),
                null, null, false, List.of(new CompraRequest.Item(jeanA, null, null, new BigDecimal("4"), new BigDecimal("40"), null)));
        CompraResponse primera = unaVez.ejecutar("clave-compra-0001", "registrar-compra", CompraResponse.class,
                () -> compraService.registrar(compra), compra);
        CompraResponse segunda = unaVez.ejecutar("clave-compra-0001", "registrar-compra", CompraResponse.class,
                () -> compraService.registrar(compra), compra);

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(segunda.total()).isEqualByComparingTo(primera.total());
        assertThat(stock(jeanA, tienda)).isEqualTo(9);
    }

    @Test
    void laMismaClaveConOtroContenidoOtroUsuarioUOtraOperacionEsUnError() {
        AjusteInsumoRequest diez = new AjusteInsumoRequest(almacen, MotivoInsumo.INVENTARIO_INICIAL, null,
                List.of(new AjusteInsumoRequest.Item(tela, new BigDecimal("10"))));
        AjusteInsumoRequest cinco = new AjusteInsumoRequest(almacen, MotivoInsumo.INVENTARIO_INICIAL, null,
                List.of(new AjusteInsumoRequest.Item(tela, new BigDecimal("5"))));
        ajustar("clave-ajuste-002", diez);

        assertThatThrownBy(() -> ajustar("clave-ajuste-002", cinco))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se usó para otra solicitud");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("otro", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(() -> ajustar("clave-ajuste-002", diez))
                .as("otro usuario no recibe la respuesta ajena").isInstanceOf(BusinessException.class);
        assertThat(stockTela()).isEqualByComparingTo("10");
    }

    @Test
    void siLaOperacionFallaNoQuedaRegistradaYElReintentoLaHace() {
        AtomicInteger intentos = new AtomicInteger();
        assertThatThrownBy(() -> unaVez.ejecutar("clave-falla-0001", "prueba", String.class, () -> {
            intentos.incrementAndGet();
            throw new BusinessException("Falló");
        })).isInstanceOf(BusinessException.class);
        assertThat(solicitudRepository.findById("clave-falla-0001")).isEmpty();

        String r = unaVez.ejecutar("clave-falla-0001", "prueba", String.class, () -> "ok " + intentos.incrementAndGet());
        assertThat(r).isEqualTo("ok 2");
        assertThat(unaVez.ejecutar("clave-falla-0001", "prueba", String.class, () -> "otra vez")).isEqualTo("ok 2");
    }

    @Test
    void lasSolicitudesViejasSeBorran() {
        unaVez.ejecutar("clave-vieja-0001", "prueba", String.class, () -> "hecho");
        solicitudRepository.flush();
        jdbc.update("update solicitud_procesada set fecha = now() - interval '8 days' where clave = 'clave-vieja-0001'");
        unaVez.limpiar();
        assertThat(solicitudRepository.findById("clave-vieja-0001")).isEmpty();
    }

    private List<MovimientoInsumoResponse> ajustar(String clave, AjusteInsumoRequest ajuste) {
        return unaVez.ejecutar(clave, "ajustar-insumos", new TypeReference<List<MovimientoInsumoResponse>>() { },
                () -> insumoService.ajustar(ajuste), ajuste);
    }

    private BigDecimal stockTela() {
        return insumoService.stock(almacen).stream().filter(s -> s.insumoId().equals(tela))
                .map(StockInsumoResponse::cantidad).findFirst().orElse(BigDecimal.ZERO);
    }
}
