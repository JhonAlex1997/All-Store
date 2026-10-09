package com.allstore.api.config;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import com.allstore.api.empresa.repository.EmpresaRepository;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.entity.Categoria;
import com.allstore.api.producto.entity.Color;
import com.allstore.api.producto.entity.Talla;
import com.allstore.api.producto.repository.CategoriaRepository;
import com.allstore.api.producto.repository.ColorRepository;
import com.allstore.api.producto.repository.TallaRepository;
import com.allstore.api.producto.service.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carga datos ficticios para probar el sistema: empresa demo, establecimientos, series,
 * productos y clientes. Solo se activa con {@code app.demo.cargar-datos=true} (perfil dev) y
 * solo sobre una base sin empresa registrada, así nunca pisa datos existentes.
 *
 * <p>Usa los mismos servicios que la API, por lo que los datos pasan las mismas validaciones
 * (RUC, reglas de serie SUNAT, SKU, códigos de barras). Todo va en una transacción: o se carga
 * completo o no se carga nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.cargar-datos", havingValue = "true")
public class DatosDemoLoader implements ApplicationRunner {

    /** RUC del entorno de pruebas de SUNAT. */
    private static final String RUC_DEMO = "20000000001";

    private final TransactionTemplate transactionTemplate;
    private final EmpresaRepository empresaRepository;
    private final EmpresaService empresaService;
    private final EstablecimientoService establecimientoService;
    private final SerieService serieService;
    private final ProductoService productoService;
    private final ClienteService clienteService;
    private final CategoriaRepository categoriaRepository;
    private final TallaRepository tallaRepository;
    private final ColorRepository colorRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (empresaRepository.count() > 0) {
            log.info("Datos demo: la base ya tiene empresa registrada, no se carga nada");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> cargar());
        log.info("Datos demo cargados: empresa {}, establecimientos, series, productos y clientes", RUC_DEMO);
    }

    private void cargar() {
        empresaService.guardar(new EmpresaRequest(RUC_DEMO, "EMPRESA DEMO S.A.C.", "AllStore Jeans (demo)",
                "Jr. Gamarra 100, La Victoria", "150115", "014567890", "ventas@demo.allstore.pe"));

        Long tienda = establecimiento("0000", "Tienda principal", TipoEstablecimiento.TIENDA,
                "Jr. Gamarra 100, La Victoria", "150115");
        Long almacen = establecimiento("0001", "Almacén central", TipoEstablecimiento.ALMACEN,
                "Av. Aviación 500, La Victoria", "150115");
        establecimiento("0002", "Taller de lavado", TipoEstablecimiento.TALLER,
                "Av. Los Frutales 200, Ate", "150103");

        serie(TipoComprobante.FACTURA, "F001", tienda);
        serie(TipoComprobante.BOLETA, "B001", tienda);
        serie(TipoComprobante.NOTA_CREDITO, "FC01", tienda);
        serie(TipoComprobante.NOTA_CREDITO, "BC01", tienda);
        serie(TipoComprobante.NOTA_DEBITO, "FD01", tienda);
        serie(TipoComprobante.NOTA_DEBITO, "BD01", tienda);
        serie(TipoComprobante.NOTA_VENTA, "NV01", tienda);
        serie(TipoComprobante.GUIA_REMISION_REMITENTE, "T001", tienda);
        serie(TipoComprobante.GUIA_REMISION_REMITENTE, "T002", almacen);

        Map<String, Long> categorias = porNombre(categoriaRepository.findAll(), Categoria::getNombre, Categoria::getId);
        Map<String, Long> tallas = porNombre(tallaRepository.findAll(), Talla::getCodigo, Talla::getId);
        Map<String, Long> colores = porNombre(colorRepository.findAll(), Color::getNombre, Color::getId);

        producto("MOM01", "Jean Mom Fit Clásico", categorias.get("Jean"), "89.90",
                ids(tallas, "26", "28", "30", "32"), ids(colores, "Azul", "Celeste"));
        producto("SKN01", "Jean Skinny Tiro Alto", categorias.get("Jean"), "79.90",
                ids(tallas, "26", "28", "30", "32"), ids(colores, "Azul", "Negro"));
        producto("REC01", "Jean Recto Hombre", categorias.get("Jean"), "99.90",
                ids(tallas, "30", "32", "34", "36", "38"), ids(colores, "Azul", "Negro"));
        producto("SHO01", "Short Denim Desflecado", categorias.get("Short"), "59.90",
                ids(tallas, "26", "28", "30"), ids(colores, "Celeste", "Blanco"));
        producto("CAS01", "Casaca Denim Clásica", categorias.get("Casaca"), "149.90",
                ids(tallas, "S", "M", "L", "XL"), ids(colores, "Azul"));

        cliente(TipoDocumentoIdentidad.DNI, "45678912", "Juan Pérez Rojas", null);
        cliente(TipoDocumentoIdentidad.DNI, "70123456", "María López Díaz", "maria.lopez@demo.pe");
        cliente(TipoDocumentoIdentidad.RUC, "20123456786", "DISTRIBUIDORA TEXTIL DEMO S.A.C.", "compras@textildemo.pe");
        cliente(TipoDocumentoIdentidad.RUC, "10456789124", "PÉREZ ROJAS JUAN (DEMO)", null);
    }

    private Long establecimiento(String codigo, String nombre, TipoEstablecimiento tipo, String direccion, String ubigeo) {
        return establecimientoService.crear(new EstablecimientoRequest(codigo, nombre, tipo, direccion, ubigeo, true)).id();
    }

    private void serie(TipoComprobante tipo, String codigo, Long establecimientoId) {
        serieService.crear(new SerieCreateRequest(tipo, codigo, establecimientoId, 0L));
    }

    private void producto(String codigo, String nombre, Long categoriaId, String precio,
                          List<Long> tallaIds, List<Long> colorIds) {
        productoService.crear(new ProductoCreateRequest(codigo, nombre, null, categoriaId,
                new BigDecimal(precio), tallaIds, colorIds));
    }

    private void cliente(TipoDocumentoIdentidad tipo, String numero, String nombre, String email) {
        clienteService.crear(new ClienteRequest(tipo, numero, nombre, null, null, null, email, true));
    }

    private static <T> Map<String, Long> porNombre(List<T> items, Function<T, String> nombre, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(nombre, id));
    }

    private static List<Long> ids(Map<String, Long> porNombre, String... nombres) {
        return Arrays.stream(nombres).map(porNombre::get).toList();
    }
}
