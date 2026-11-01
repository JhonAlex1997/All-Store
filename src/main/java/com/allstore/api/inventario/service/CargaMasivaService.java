package com.allstore.api.inventario.service;

import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.inventario.dto.CargaMasivaResponse;
import com.allstore.api.inventario.dto.CargaMasivaResponse.ErrorFila;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.VariantesRequest;
import com.allstore.api.producto.entity.Categoria;
import com.allstore.api.producto.entity.Color;
import com.allstore.api.producto.entity.Producto;
import com.allstore.api.producto.entity.Talla;
import com.allstore.api.producto.repository.CategoriaRepository;
import com.allstore.api.producto.repository.ColorRepository;
import com.allstore.api.producto.repository.ProductoRepository;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.producto.repository.TallaRepository;
import com.allstore.api.producto.service.CodigoVariante;
import com.allstore.api.producto.service.ProductoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carga masiva del catálogo y del inventario inicial desde un Excel: una fila por prenda (modelo +
 * talla + color) con su stock. Crea los modelos y las combinaciones que no existen y registra el
 * stock como INVENTARIO_INICIAL en el local indicado. Es todo o nada: si alguna fila tiene un
 * error no se guarda nada y la respuesta dice qué corregir en cada fila. Con aplicar=false solo
 * revisa (para ver el resultado antes de cargar).
 *
 * <p>Columnas (en cualquier orden; el encabezado no distingue mayúsculas ni tildes): codigo,
 * nombre, categoria, precio, precio_por_mayor (opcional), talla, color, stock (opcional). Las
 * categorías, tallas y colores deben existir (se reconocen por su nombre o código).
 */
@Service
@RequiredArgsConstructor
public class CargaMasivaService {

    static final int MAXIMO_FILAS = 5000;
    private static final List<String> OBLIGATORIAS = List.of("codigo", "nombre", "categoria", "precio", "talla", "color");
    private static final List<String> COLUMNAS = List.of("codigo", "nombre", "categoria", "precio", "precio_por_mayor",
            "talla", "color", "stock");

    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final CategoriaRepository categoriaRepository;
    private final TallaRepository tallaRepository;
    private final ColorRepository colorRepository;
    private final Bitacora bitacora;
    private final Validator validator;

    /** Una fila del Excel ya interpretada (los nulos son datos que faltan o no se reconocieron). */
    private record Fila(int numero, String codigo, String nombre, Categoria categoria, BigDecimal precio,
                        BigDecimal precioPorMayor, Talla talla, Color color, int stock) {
    }

    @Transactional
    public CargaMasivaResponse cargar(InputStream archivo, Long establecimientoId, boolean aplicar) {
        List<ErrorFila> errores = new ArrayList<>();
        List<Fila> filas = leer(archivo, errores);
        if (filas.isEmpty() && errores.isEmpty()) {
            errores.add(new ErrorFila(1, "El archivo no tiene filas con datos"));
        }
        Plan plan = planificar(filas, errores);
        if (!errores.isEmpty() || !aplicar) {
            errores.sort(Comparator.comparingInt(ErrorFila::fila));
            return new CargaMasivaResponse(false, filas.size(), plan.modelosNuevos.size(), plan.variantesNuevas.size(),
                    plan.unidades(), errores);
        }

        for (Fila modelo : plan.modelosNuevos.values()) {
            productoService.crear(new ProductoCreateRequest(modelo.codigo(), modelo.nombre(), null, modelo.categoria().getId(),
                    modelo.precio(), modelo.precioPorMayor(), null, null));
        }
        for (Fila fila : plan.variantesNuevas) {
            Producto producto = productoRepository.findByCodigoIgnoreCase(fila.codigo()).orElseThrow();
            productoService.agregarVariantes(producto.getId(),
                    new VariantesRequest(List.of(fila.talla().getId()), List.of(fila.color().getId())));
        }
        List<InventarioService.Linea> lineas = plan.variantesNuevas.stream().filter(f -> f.stock() > 0)
                .map(f -> new InventarioService.Linea(varianteRepository.findByCodigoBarrasOrSku(sku(f)).orElseThrow().getId(), f.stock()))
                .toList();
        if (!lineas.isEmpty()) {
            inventarioService.registrar(establecimientoId, MotivoMovimiento.INVENTARIO_INICIAL, lineas, "Carga masiva");
        }
        bitacora.registrar(AccionBitacora.CARGA_MASIVA, "INVENTARIO", establecimientoId, "Cargó " + filas.size()
                + " filas: " + plan.modelosNuevos.size() + " modelos nuevos, " + plan.variantesNuevas.size()
                + " combinaciones y " + plan.unidades() + " prendas");
        return new CargaMasivaResponse(true, filas.size(), plan.modelosNuevos.size(), plan.variantesNuevas.size(),
                plan.unidades(), List.of());
    }

    /** Plantilla para llenar: encabezados, dos filas de ejemplo y una hoja con lo que el sistema reconoce. */
    @Transactional(readOnly = true)
    public byte[] plantilla() {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Productos");
            escribir(hoja, 0, COLUMNAS.toArray(String[]::new));
            escribir(hoja, 1, "MOM02", "Jean Mom Vintage", "Jean", "94.90", "68.00", "28", "Azul", "6");
            escribir(hoja, 2, "MOM02", "Jean Mom Vintage", "Jean", "94.90", "68.00", "30", "Azul", "4");
            for (int i = 0; i < COLUMNAS.size(); i++) {
                hoja.setColumnWidth(i, 18 * 256);
            }
            Sheet catalogo = libro.createSheet("Catálogo");
            escribir(catalogo, 0, "categoria", "talla", "color");
            List<String> categorias = categoriaRepository.findAllByOrderByNombreAsc().stream()
                    .filter(Categoria::isActivo).map(Categoria::getNombre).toList();
            List<String> tallas = tallaRepository.findAllByOrderByOrdenAscCodigoAsc().stream()
                    .filter(Talla::isActivo).map(Talla::getCodigo).toList();
            List<String> colores = colorRepository.findAllByOrderByNombreAsc().stream()
                    .filter(Color::isActivo).map(Color::getNombre).toList();
            int maximo = Math.max(categorias.size(), Math.max(tallas.size(), colores.size()));
            for (int i = 0; i < maximo; i++) {
                escribir(catalogo, i + 1, i < categorias.size() ? categorias.get(i) : "",
                        i < tallas.size() ? tallas.get(i) : "", i < colores.size() ? colores.get(i) : "");
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    // ---------- Lectura ----------

    private List<Fila> leer(InputStream archivo, List<ErrorFila> errores) {
        Map<String, Categoria> categorias = indice(categoriaRepository.findAll(), Categoria::getNombre, null);
        Map<String, Talla> tallas = indice(tallaRepository.findAll(), Talla::getCodigo, null);
        Map<String, Color> colores = indice(colorRepository.findAll(), Color::getNombre, Color::getCodigo);
        DataFormatter formato = new DataFormatter(Locale.ROOT);
        List<Fila> filas = new ArrayList<>();
        try (Workbook libro = new XSSFWorkbook(archivo)) {
            Sheet hoja = libro.getSheetAt(0);
            Map<String, Integer> columnas = encabezados(hoja.getRow(hoja.getFirstRowNum()), formato);
            List<String> faltan = OBLIGATORIAS.stream().filter(c -> !columnas.containsKey(c)).toList();
            if (!faltan.isEmpty()) {
                errores.add(new ErrorFila(1, "Faltan las columnas: " + String.join(", ", faltan)
                        + ". Descarga la plantilla para ver el formato"));
                return filas;
            }
            for (int i = hoja.getFirstRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || vacia(fila, formato)) {
                    continue;
                }
                if (filas.size() >= MAXIMO_FILAS) {
                    errores.add(new ErrorFila(i + 1, "Máximo " + MAXIMO_FILAS + " filas por carga: divide el archivo"));
                    break;
                }
                filas.add(interpretar(i + 1, fila, columnas, formato, categorias, tallas, colores, errores));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException negocio) {
                throw negocio;
            }
            errores.add(new ErrorFila(1, "No se pudo leer el archivo: debe ser un Excel .xlsx (" + e.getMessage() + ")"));
        }
        return filas;
    }

    private Fila interpretar(int numero, Row fila, Map<String, Integer> columnas, DataFormatter formato,
                             Map<String, Categoria> categorias, Map<String, Talla> tallas, Map<String, Color> colores,
                             List<ErrorFila> errores) {
        Function<String, String> celda = nombre -> {
            Integer indice = columnas.get(nombre);
            Cell c = indice == null ? null : fila.getCell(indice);
            String valor = c == null ? "" : formato.formatCellValue(c).trim();
            return valor.isEmpty() ? null : valor;
        };
        String codigo = celda.apply("codigo") == null ? null : celda.apply("codigo").toUpperCase();
        String categoriaTexto = celda.apply("categoria");
        String tallaTexto = celda.apply("talla");
        String colorTexto = celda.apply("color");
        Categoria categoria = categoriaTexto == null ? null : categorias.get(clave(categoriaTexto));
        Talla talla = tallaTexto == null ? null : tallas.get(clave(tallaTexto));
        Color color = colorTexto == null ? null : colores.get(clave(colorTexto));
        List<String> problemas = new ArrayList<>();
        if (codigo == null) {
            problemas.add("falta el código");
        }
        if (celda.apply("nombre") == null) {
            problemas.add("falta el nombre");
        }
        if (categoria == null) {
            problemas.add(categoriaTexto == null ? "falta la categoría" : "la categoría '" + categoriaTexto + "' no existe");
        }
        if (talla == null) {
            problemas.add(tallaTexto == null ? "falta la talla" : "la talla '" + tallaTexto + "' no existe");
        }
        if (color == null) {
            problemas.add(colorTexto == null ? "falta el color" : "el color '" + colorTexto + "' no existe");
        }
        BigDecimal precio = decimal(celda.apply("precio"), "el precio", problemas, true);
        BigDecimal precioPorMayor = decimal(celda.apply("precio_por_mayor"), "el precio por mayor", problemas, false);
        int stock = entero(celda.apply("stock"), problemas);
        if (!problemas.isEmpty()) {
            errores.add(new ErrorFila(numero, mayuscula(String.join("; ", problemas))));
        }
        return new Fila(numero, codigo, celda.apply("nombre"), categoria, precio, precioPorMayor, talla, color, stock);
    }

    // ---------- Plan: qué se crea ----------

    private static final class Plan {
        final Map<String, Fila> modelosNuevos = new LinkedHashMap<>();
        final List<Fila> variantesNuevas = new ArrayList<>();

        int unidades() {
            return variantesNuevas.stream().mapToInt(Fila::stock).sum();
        }
    }

    private Plan planificar(List<Fila> filas, List<ErrorFila> errores) {
        Plan plan = new Plan();
        Set<Integer> conError = errores.stream().map(ErrorFila::fila).collect(Collectors.toSet());
        Set<String> vistas = new HashSet<>();
        Map<String, Fila> primeraDelModelo = new HashMap<>();
        for (Fila fila : filas) {
            if (conError.contains(fila.numero())) {
                continue;
            }
            String sku = sku(fila);
            if (!vistas.add(sku)) {
                errores.add(new ErrorFila(fila.numero(), sku + " está repetido en el archivo"));
                continue;
            }
            Fila primera = primeraDelModelo.putIfAbsent(fila.codigo(), fila);
            if (primera != null && (!primera.nombre().equals(fila.nombre()) || primera.precio().compareTo(fila.precio()) != 0
                    || primera.categoria() != fila.categoria())) {
                errores.add(new ErrorFila(fila.numero(), "El modelo " + fila.codigo() + " tiene otro nombre, categoría o precio "
                        + "que en la fila " + primera.numero() + ": deben ser iguales en todas sus filas"));
                continue;
            }
            boolean modeloExiste = productoRepository.existsByCodigoIgnoreCase(fila.codigo());
            if (!modeloExiste && primera == null) {
                List<String> invalidos = validator.validate(new ProductoCreateRequest(fila.codigo(), fila.nombre(), null,
                                fila.categoria().getId(), fila.precio(), fila.precioPorMayor(), null, null)).stream()
                        .map(ConstraintViolation::getMessage).sorted().toList();
                if (!invalidos.isEmpty()) {
                    errores.add(new ErrorFila(fila.numero(), String.join("; ", invalidos)));
                    continue;
                }
                if (fila.precioPorMayor() != null && fila.precioPorMayor().compareTo(fila.precio()) > 0) {
                    errores.add(new ErrorFila(fila.numero(), "El precio por mayor no puede ser mayor que el precio"));
                    continue;
                }
                plan.modelosNuevos.put(fila.codigo(), fila);
            }
            if (modeloExiste && varianteRepository.findByCodigoBarrasOrSku(sku).isPresent()) {
                if (fila.stock() > 0) {
                    errores.add(new ErrorFila(fila.numero(), sku + " ya existe: su stock se suma con un ajuste de inventario, "
                            + "no con la carga masiva"));
                }
                continue;
            }
            plan.variantesNuevas.add(fila);
        }
        return plan;
    }

    // ---------- Ayudas ----------

    private static String sku(Fila fila) {
        return CodigoVariante.sku(fila.codigo(), fila.color().getCodigo(), fila.talla().getCodigo());
    }

    private static Map<String, Integer> encabezados(Row fila, DataFormatter formato) {
        Map<String, Integer> columnas = new HashMap<>();
        if (fila != null) {
            for (Cell c : fila) {
                columnas.putIfAbsent(clave(formato.formatCellValue(c)).replace(' ', '_'), c.getColumnIndex());
            }
        }
        return columnas;
    }

    private static <T> Map<String, T> indice(List<T> elementos, Function<T, String> nombre, Function<T, String> codigo) {
        Map<String, T> indice = new HashMap<>();
        for (T e : elementos) {
            indice.put(clave(nombre.apply(e)), e);
            if (codigo != null) {
                indice.putIfAbsent(clave(codigo.apply(e)), e);
            }
        }
        return indice;
    }

    /** Sin tildes, en minúsculas y sin espacios de más: "Categoría " y "categoria" son lo mismo. */
    private static String clave(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static BigDecimal decimal(String valor, String nombre, List<String> problemas, boolean obligatorio) {
        if (valor == null) {
            if (obligatorio) {
                problemas.add("falta " + nombre);
            }
            return null;
        }
        try {
            BigDecimal numero = new BigDecimal(valor.replace("S/", "").replace(",", ".").trim());
            if (numero.signum() < 0) {
                problemas.add(nombre + " no puede ser negativo");
                return null;
            }
            return numero.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            problemas.add(nombre + " '" + valor + "' no es un número");
            return null;
        }
    }

    private static int entero(String valor, List<String> problemas) {
        if (valor == null) {
            return 0;
        }
        try {
            int numero = new BigDecimal(valor.replace(",", ".")).intValueExact();
            if (numero < 0 || numero > 100_000) {
                problemas.add("el stock debe estar entre 0 y 100000");
                return 0;
            }
            return numero;
        } catch (NumberFormatException | ArithmeticException e) {
            problemas.add("el stock '" + valor + "' debe ser un número entero");
            return 0;
        }
    }

    private static boolean vacia(Row fila, DataFormatter formato) {
        for (Cell c : fila) {
            if (!formato.formatCellValue(c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void escribir(Sheet hoja, int numero, String... valores) {
        Row fila = hoja.createRow(numero);
        for (int i = 0; i < valores.length; i++) {
            fila.createCell(i).setCellValue(valores[i]);
        }
    }

    private static String mayuscula(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
