package com.allstore.api.venta;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.inventario.dto.StockEstablecimientoResponse;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.service.ProductoService;
import com.allstore.api.venta.dto.PagoRequest;
import com.allstore.api.venta.dto.VentaItemRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.service.AnulacionService;
import com.allstore.api.venta.service.VentaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos cajas que venden la última unidad en el mismo instante: el bloqueo del stock (SELECT ... FOR UPDATE)
 * debe dejar pasar solo una venta y el stock no puede quedar en -1.
 *
 * <p>Sin @Transactional: cada venta corre en su propia transacción y en su propio hilo, como dos cajas
 * reales. Los datos usan códigos al azar para no chocar con otras pruebas en la base allstore_test.
 */
@SpringBootTest
class VentaSimultaneaIntegrationTest {

    @Autowired private VentaService ventaService;
    @Autowired private CajaService cajaService;
    @Autowired private ProductoService productoService;
    @Autowired private InventarioService inventarioService;
    @Autowired private EstablecimientoService establecimientoService;
    @Autowired private SerieService serieService;
    @Autowired private AnulacionService anulacionService;
    @Autowired private com.allstore.api.empresa.repository.EstablecimientoRepository establecimientoRepository;
    @Autowired private com.allstore.api.empresa.repository.SerieRepository serieRepository;
    @Autowired private com.allstore.api.producto.repository.ProductoRepository productoRepository;

    /**
     * Sufijo de 3 dígitos que aún no usa ningún local, serie ni modelo: estas pruebas confirman sus datos
     * (no se revierten) y con un sufijo al azar repetían códigos de corridas anteriores.
     */
    private String sufijoLibre(String local, String serie, String modelo) {
        for (int intento = 0; intento < 200; intento++) {
            String sufijo = String.valueOf(ThreadLocalRandom.current().nextInt(100, 1000));
            if (!establecimientoRepository.existsByCodigo(local + sufijo)
                    && !serieRepository.existsByTipoComprobanteAndCodigo(TipoComprobante.NOTA_VENTA, serie + sufijo)
                    && !productoRepository.existsByCodigoIgnoreCase(modelo + sufijo)) {
                return sufijo;
            }
        }
        throw new IllegalStateException("No quedan sufijos libres para " + local + "/" + serie + "/" + modelo);
    }

    @AfterEach
    void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dosCajasVendenLaUltimaUnidadALaVezYSoloPasaUna() throws Exception {
        String sufijo = sufijoLibre("8", "S", "SIM-");
        como("t_sim_admin");
        Long local = establecimientoService.crear(new EstablecimientoRequest("8" + sufijo, "Local simultaneo " + sufijo,
                TipoEstablecimiento.TIENDA, "Jr. Prueba 1", "150115", true)).id();
        Long serie = serieService.crear(new SerieCreateRequest(TipoComprobante.NOTA_VENTA, "S" + sufijo, local, 0L)).id();
        Long prenda = productoService.crear(new ProductoCreateRequest("SIM-" + sufijo, "Jean simultaneo", null, 1L,
                new BigDecimal("89.90"), null, List.of(3L), List.of(1L))).variantes().getFirst().id();
        inventarioService.registrar(local, MotivoMovimiento.INVENTARIO_INICIAL, List.of(new InventarioService.Linea(prenda, 1)), null);

        List<String> cajeros = List.of("t_sim_a" + sufijo, "t_sim_b" + sufijo);
        for (String cajero : cajeros) {
            como(cajero);
            cajaService.abrir(new AbrirCajaRequest(local, BigDecimal.ZERO));
        }

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<String> resultados = Collections.synchronizedList(new ArrayList<>());
        List<Long> vendidas = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> tareas = new ArrayList<>();
        for (String cajero : cajeros) {
            tareas.add(hilos.submit(() -> {
                como(cajero);
                try {
                    salida.await();
                    VentaResponse venta = ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serie, null, null,
                            List.of(new VentaItemRequest(prenda, 1, null)),
                            List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("89.90"), null, null)),
                            null, null, null));
                    vendidas.add(venta.id());
                    resultados.add("vendió");
                } catch (Exception e) {
                    resultados.add("rechazada: " + e.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> t : tareas) {
            t.get(30, TimeUnit.SECONDS);
        }
        hilos.shutdown();

        assertThat(resultados).hasSize(2);
        assertThat(resultados).filteredOn("vendió"::equals).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.startsWith("rechazada")).singleElement()
                .asString().containsIgnoringCase("stock");
        int stock = inventarioService.stockPorVariante(prenda).stream()
                .filter(s -> s.establecimientoId().equals(local))
                .mapToInt(StockEstablecimientoResponse::cantidad).findFirst().orElseThrow();
        assertThat(stock).as("el stock no puede quedar negativo").isZero();

        // Las ventas de esta prueba quedan guardadas (no hay rollback): se anulan para no alterar los
        // conteos del mes que revisan otras pruebas (por ejemplo, el registro de ventas).
        for (int i = 0; i < cajeros.size(); i++) {
            como(cajeros.get(i));
            for (Long id : vendidas) {
                try {
                    anulacionService.anular(id, new AnularVentaRequest("Fin de la prueba de concurrencia"));
                } catch (Exception yaAnuladaOCajaAjena) {
                    // la anula el cajero que la cobró
                }
            }
        }
    }

    /**
     * Doble clic real: el mismo cajero manda la misma venta dos veces a la vez, con la misma clave.
     * Debe quedar una sola venta (las dos respuestas son la misma) y el stock baja una sola vez.
     */
    @Test
    void elDobleClicSimultaneoConLaMismaClaveRegistraUnaSolaVenta() throws Exception {
        String sufijo = sufijoLibre("7", "D", "DBL-");
        como("t_sim_admin");
        Long local = establecimientoService.crear(new EstablecimientoRequest("7" + sufijo, "Local doble clic " + sufijo,
                TipoEstablecimiento.TIENDA, "Jr. Prueba 1", "150115", true)).id();
        Long serie = serieService.crear(new SerieCreateRequest(TipoComprobante.NOTA_VENTA, "D" + sufijo, local, 0L)).id();
        Long prenda = productoService.crear(new ProductoCreateRequest("DBL-" + sufijo, "Jean doble clic", null, 1L,
                new BigDecimal("89.90"), null, List.of(3L), List.of(1L))).variantes().getFirst().id();
        inventarioService.registrar(local, MotivoMovimiento.INVENTARIO_INICIAL, List.of(new InventarioService.Linea(prenda, 5)), null);
        String cajero = "t_dbl_" + sufijo;
        como(cajero);
        cajaService.abrir(new AbrirCajaRequest(local, BigDecimal.ZERO));

        String clave = java.util.UUID.randomUUID().toString();
        VentaRequest venta = new VentaRequest(TipoComprobante.NOTA_VENTA, serie, null, null,
                List.of(new VentaItemRequest(prenda, 1, null)),
                List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("89.90"), null, null)), null, null, null);

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<VentaResponse>> clics = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            clics.add(hilos.submit(() -> {
                como(cajero);
                try {
                    salida.await();
                    return ventaService.crear(venta, clave);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        salida.countDown();
        VentaResponse primero = clics.get(0).get(30, TimeUnit.SECONDS);
        VentaResponse segundo = clics.get(1).get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        assertThat(segundo.id()).as("los dos clics devuelven la misma venta").isEqualTo(primero.id());
        int stock = inventarioService.stockPorVariante(prenda).stream()
                .filter(s -> s.establecimientoId().equals(local))
                .mapToInt(StockEstablecimientoResponse::cantidad).findFirst().orElseThrow();
        assertThat(stock).as("el stock baja una sola vez").isEqualTo(4);

        como(cajero);
        anulacionService.anular(primero.id(), new AnularVentaRequest("Fin de la prueba de doble clic"));
    }

    /**
     * Varias cajas cobran a la vez con la misma serie y hay stock de sobra: todas las ventas deben
     * pasar con números seguidos y sin repetir. (Lo encontró la prueba de carga: la serie ya leída
     * en la transacción no se releía al bloquearla y dos cajas tomaban el mismo correlativo.)
     */
    @Test
    void variasCajasALaVezTomanNumerosDistintosYSeguidos() throws Exception {
        String sufijo = sufijoLibre("6", "N", "NUM-");
        como("t_num_admin");
        Long local = establecimientoService.crear(new EstablecimientoRequest("6" + sufijo, "Local numeracion " + sufijo,
                TipoEstablecimiento.TIENDA, "Jr. Prueba 1", "150115", true)).id();
        Long serie = serieService.crear(new SerieCreateRequest(TipoComprobante.NOTA_VENTA, "N" + sufijo, local, 0L)).id();
        Long prenda = productoService.crear(new ProductoCreateRequest("NUM-" + sufijo, "Jean numeracion", null, 1L,
                new BigDecimal("89.90"), null, List.of(3L), List.of(1L))).variantes().getFirst().id();
        inventarioService.registrar(local, MotivoMovimiento.INVENTARIO_INICIAL, List.of(new InventarioService.Linea(prenda, 100)), null);

        int cajas = 6;
        int ventasPorCaja = 5;
        List<String> cajeros = new ArrayList<>();
        for (int i = 0; i < cajas; i++) {
            String cajero = "t_num_" + i + "_" + sufijo;
            cajeros.add(cajero);
            como(cajero);
            cajaService.abrir(new AbrirCajaRequest(local, BigDecimal.ZERO));
        }

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(cajas);
        List<VentaResponse> ventas = Collections.synchronizedList(new ArrayList<>());
        List<String> errores = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> tareas = new ArrayList<>();
        for (String cajero : cajeros) {
            tareas.add(hilos.submit(() -> {
                como(cajero);
                try {
                    salida.await();
                    for (int v = 0; v < ventasPorCaja; v++) {
                        try {
                            ventas.add(ventaService.crear(new VentaRequest(TipoComprobante.NOTA_VENTA, serie, null, null,
                                    List.of(new VentaItemRequest(prenda, 1, null)),
                                    List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("89.90"), null, null)),
                                    null, null, null)));
                        } catch (Exception e) {
                            errores.add(e.getMessage());
                        }
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> t : tareas) {
            t.get(60, TimeUnit.SECONDS);
        }
        hilos.shutdown();

        int total = cajas * ventasPorCaja;
        try {
            assertThat(errores).as("ninguna venta debe fallar").isEmpty();
            assertThat(ventas).hasSize(total);
            assertThat(ventas).extracting(VentaResponse::numero).as("números sin repetir").doesNotHaveDuplicates();
            assertThat(ventas.stream().map(v -> Long.parseLong(v.numero().substring(v.numero().indexOf('-') + 1))).sorted().toList())
                    .as("números seguidos, sin huecos").isEqualTo(java.util.stream.LongStream.rangeClosed(1, total).boxed().toList());
            int stock = inventarioService.stockPorVariante(prenda).stream()
                    .filter(s -> s.establecimientoId().equals(local))
                    .mapToInt(StockEstablecimientoResponse::cantidad).findFirst().orElseThrow();
            assertThat(stock).isEqualTo(100 - total);
        } finally {
            // Se anulan aunque la prueba falle, para no alterar los conteos del mes de otras pruebas
            // (cada una la anula su cajero).
            for (String cajero : cajeros) {
                como(cajero);
                for (VentaResponse v : ventas) {
                    try {
                        anulacionService.anular(v.id(), new AnularVentaRequest("Fin de la prueba de numeración"));
                    } catch (Exception yaAnuladaOCajaAjena) {
                        // la anula el cajero que la cobró
                    }
                }
            }
        }
    }

    private static void como(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
