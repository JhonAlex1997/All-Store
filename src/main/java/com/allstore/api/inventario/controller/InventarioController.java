package com.allstore.api.inventario.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.inventario.dto.CargaMasivaResponse;
import com.allstore.api.inventario.dto.MovimientoManualRequest;
import com.allstore.api.inventario.dto.MovimientoResponse;
import com.allstore.api.inventario.dto.ReposicionFila;
import com.allstore.api.inventario.dto.StockEstablecimientoResponse;
import com.allstore.api.inventario.dto.StockMinimoRequest;
import com.allstore.api.inventario.dto.StockResponse;
import com.allstore.api.inventario.dto.TrasladoRequest;
import com.allstore.api.inventario.service.CargaMasivaService;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.inventario.service.ReposicionService;
import com.allstore.api.reporte.exportacion.RespuestaReporte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;
    private final UnaVez unaVez;
    private final CargaMasivaService cargaMasivaService;
    private final ReposicionService reposicionService;
    private final RespuestaReporte respuestaReporte;

    /**
     * Qué reponer: prendas en o bajo su stock mínimo (primero las agotadas), con lo que rotan y lo que
     * hay en otros locales para trasladar. Con formato=XLSX o CSV, como archivo.
     */
    @GetMapping("/reposicion")
    public ResponseEntity<?> reposicion(@RequestParam(required = false) Long establecimientoId,
                                        @RequestParam(defaultValue = "JSON") RespuestaReporte.Formato formato) {
        return respuestaReporte.responder(reposicionService.reporte(establecimientoId), formato);
    }

    /**
     * Mínimos propios de prendas en un local (minimo = null quita el propio y vuelve a regir el del
     * modelo). Responde la reposición pendiente de ese local.
     */
    @PutMapping("/stock-minimo")
    public List<ReposicionFila> definirStockMinimo(@Valid @RequestBody StockMinimoRequest request) {
        return reposicionService.definirMinimos(request);
    }

    /** Ej: GET /api/inventario/stock?establecimientoId=1&busqueda=mom&soloConStock=true */
    @GetMapping("/stock")
    public PageResponse<StockResponse> stock(
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "false") boolean soloConStock,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return inventarioService.stock(establecimientoId, busqueda, soloConStock, pageable);
    }

    /** En qué establecimientos hay una prenda y cuántas. */
    @GetMapping("/stock/variante/{varianteId}")
    public List<StockEstablecimientoResponse> stockPorVariante(@PathVariable Long varianteId) {
        return inventarioService.stockPorVariante(varianteId);
    }

    /** Ej: GET /api/inventario/kardex?varianteId=3&establecimientoId=1&desde=2026-10-01&hasta=2026-10-31 */
    @GetMapping("/kardex")
    public PageResponse<MovimientoResponse> kardex(
            @RequestParam(required = false) Long varianteId,
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return inventarioService.kardex(varianteId, establecimientoId, desde, hasta, pageable);
    }

    /** Inventario inicial o ajuste por conteo (sobrante, faltante, merma). */
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MovimientoResponse> registrarMovimiento(@Valid @RequestBody MovimientoManualRequest request, @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return inventarioService.registrarManual(request, clave);
    }

    @PostMapping("/traslados")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MovimientoResponse> trasladar(@Valid @RequestBody TrasladoRequest request, @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return inventarioService.trasladar(request, clave);
    }

    /**
     * Carga masiva desde Excel: crea modelos y combinaciones y registra el stock inicial en el
     * local. Con aplicar=false (por defecto) solo revisa y dice qué corregir; con aplicar=true guarda,
     * y si hay algún error no guarda nada. Ver CargaMasivaService para las columnas.
     */
    @PostMapping(value = "/carga-masiva", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargaMasivaResponse cargaMasiva(@RequestParam("archivo") MultipartFile archivo,
                                           @RequestParam Long establecimientoId,
                                           @RequestParam(defaultValue = "false") boolean aplicar,
                                           @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave)
            throws IOException {
        byte[] contenido = archivo.getBytes();
        if (!aplicar) {
            // Solo revisa: no cambia nada, se puede repetir.
            return cargaMasivaService.cargar(new ByteArrayInputStream(contenido), establecimientoId, false);
        }
        // Aplicar dos veces sumaría el stock dos veces: con la clave, un reintento recibe el resultado ya dado.
        String huellaArchivo = HexFormat.of().formatHex(sha256(contenido));
        return unaVez.ejecutar(clave, "carga-masiva", CargaMasivaResponse.class,
                () -> cargaMasivaService.cargar(new ByteArrayInputStream(contenido), establecimientoId, true),
                establecimientoId, huellaArchivo);
    }

    private static byte[] sha256(byte[] contenido) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(contenido);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Plantilla de Excel para la carga masiva, con las categorías, tallas y colores que reconoce el sistema. */
    @GetMapping("/carga-masiva/plantilla")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("plantilla-carga-masiva.xlsx").build().toString())
                .body(cargaMasivaService.plantilla());
    }
}
