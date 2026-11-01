package com.allstore.api.contabilidad.controller;

import com.allstore.api.contabilidad.service.AsientoService;
import com.allstore.api.reporte.exportacion.RespuestaReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Libro diario y mayor del periodo (solo ADMIN, ver SecurityConfig: /api/contabilidad/**). */
@RestController
@RequestMapping("/api/contabilidad/libro-diario")
@RequiredArgsConstructor
public class LibroDiarioController {

    private final AsientoService asientoService;
    private final RespuestaReporte respuestaReporte;

    /**
     * Ej: GET /api/contabilidad/libro-diario?periodo=2026-11. En JSON trae los asientos, el mayor y las
     * advertencias; con formato=XLSX o CSV, el diario línea por línea.
     */
    @GetMapping
    public ResponseEntity<?> libroDiario(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth periodo,
                                         @RequestParam(defaultValue = "JSON") RespuestaReporte.Formato formato) {
        return formato == RespuestaReporte.Formato.JSON ? ResponseEntity.ok(asientoService.libroDiario(periodo))
                : respuestaReporte.responder(asientoService.reporte(periodo), formato);
    }
}
