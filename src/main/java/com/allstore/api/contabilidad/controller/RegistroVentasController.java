package com.allstore.api.contabilidad.controller;

import com.allstore.api.contabilidad.dto.RegistroVentasResponse;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse;
import com.allstore.api.contabilidad.service.ArchivoRvie;
import com.allstore.api.contabilidad.service.RegistroVentasService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Registro de Ventas e Ingresos Electrónico (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/contabilidad/registro-ventas")
@RequiredArgsConstructor
public class RegistroVentasController {

    public enum FormatoArchivo {
        /** Como lo recibe el SIRE para reemplazar la propuesta. */
        ZIP,
        /** El texto plano, para revisarlo o pasarlo por el validador PVSIRE. */
        TXT
    }

    private final RegistroVentasService registroVentasService;

    /** Ej: GET /api/contabilidad/registro-ventas?periodo=2026-10 */
    @GetMapping
    public RegistroVentasResponse registro(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return registroVentasService.generar(periodo);
    }

    /**
     * Revisa el archivo del SIRE con las reglas de la estructura oficial de SUNAT (como el PVSIRE): fila,
     * campo y motivo de cada inconsistencia.
     */
    @GetMapping("/validar")
    public ValidacionSireResponse validar(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return registroVentasService.validar(periodo);
    }

    /** Archivo para reemplazar la propuesta del RVIE en el SIRE. Ej: GET .../sire?periodo=2026-10 */
    @GetMapping("/sire")
    public ResponseEntity<byte[]> archivoSire(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
                                              @RequestParam(defaultValue = "ZIP") FormatoArchivo formato) {
        ArchivoRvie.Archivo archivo = registroVentasService.archivoSire(periodo, formato == FormatoArchivo.ZIP);
        return ResponseEntity.ok()
                .contentType(formato == FormatoArchivo.ZIP ? MediaType.parseMediaType("application/zip") : MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
