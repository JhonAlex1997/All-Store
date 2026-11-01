package com.allstore.api.contabilidad.controller;

import com.allstore.api.contabilidad.dto.RegistroComprasResponse;
import com.allstore.api.contabilidad.dto.ValidacionSireResponse;
import com.allstore.api.contabilidad.service.ArchivoRvie;
import com.allstore.api.contabilidad.service.RegistroComprasService;
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

/** Registro de Compras Electrónico (solo ADMIN, ver SecurityConfig: /api/contabilidad/**). */
@RestController
@RequestMapping("/api/contabilidad/registro-compras")
@RequiredArgsConstructor
public class RegistroComprasController {

    private final RegistroComprasService registroComprasService;

    /** Ej: GET /api/contabilidad/registro-compras?periodo=2026-11 */
    @GetMapping
    public RegistroComprasResponse registro(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return registroComprasService.generar(periodo);
    }

    /** Revisa el archivo del SIRE con las reglas de la estructura oficial de SUNAT (como el PVSIRE). */
    @GetMapping("/validar")
    public ValidacionSireResponse validar(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo) {
        return registroComprasService.validar(periodo);
    }

    /** Archivo para reemplazar la propuesta del RCE en el SIRE. */
    @GetMapping("/sire")
    public ResponseEntity<byte[]> archivoSire(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
                                              @RequestParam(defaultValue = "ZIP") RegistroVentasController.FormatoArchivo formato) {
        boolean zip = formato == RegistroVentasController.FormatoArchivo.ZIP;
        ArchivoRvie.Archivo archivo = registroComprasService.archivoSire(periodo, zip);
        return ResponseEntity.ok()
                .contentType(zip ? MediaType.parseMediaType("application/zip") : MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
