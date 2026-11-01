package com.allstore.api.reporte.controller;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.reporte.exportacion.Reporte;
import com.allstore.api.reporte.exportacion.RespuestaReporte;
import com.allstore.api.reporte.exportacion.RespuestaReporte.Formato;
import com.allstore.api.reporte.service.ReporteService;
import com.allstore.api.reporte.service.ReporteService.Periodo;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reportes para decidir (solo ADMIN, ver SecurityConfig). Todos aceptan {@code desde}, {@code hasta}
 * (por defecto, el mes en curso) y {@code establecimientoId}, y responden en JSON o, con
 * {@code formato=XLSX} o {@code formato=CSV}, como archivo para Excel o para análisis en Python.
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;
    private final RespuestaReporte respuesta;

    /** Qué modelos se venden más: unidades netas, total y % del total. */
    @GetMapping("/ventas-por-producto")
    public ResponseEntity<?> porProducto(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Long establecimientoId,
                                         @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porProducto(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Cuánto vendió y cuánto descontó cada campaña de promoción. */
    @GetMapping("/ventas-por-campana")
    public ResponseEntity<?> porCampana(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                        @RequestParam(required = false) Long establecimientoId,
                                        @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porCampana(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Qué tallas y colores salen, con su stock actual para reponer. */
    @GetMapping("/ventas-por-talla-color")
    public ResponseEntity<?> porTallaColor(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           @RequestParam(required = false) Long establecimientoId,
                                           @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porTallaColor(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Ventas, devoluciones, ticket promedio, descuentos y por mayor manual de cada vendedor. */
    @GetMapping("/ventas-por-vendedor")
    public ResponseEntity<?> porVendedor(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(required = false) Long establecimientoId,
                                         @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porVendedor(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Cuántas ventas hay en cada hora del día (hora de Lima). */
    @GetMapping("/ventas-por-hora")
    public ResponseEntity<?> porHora(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(required = false) Long establecimientoId,
                                     @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porHora(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Ventas por día de la semana (lunes a domingo). */
    @GetMapping("/ventas-por-dia-semana")
    public ResponseEntity<?> porDiaSemana(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) Long establecimientoId,
                                          @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porDiaSemana(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Ventas, devoluciones y neto de cada día. */
    @GetMapping("/ventas-por-dia")
    public ResponseEntity<?> porDia(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) Long establecimientoId,
                                    @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.porDia(periodo(desde, hasta, establecimientoId)), formato);
    }

    /** Prendas con stock que no se venden hace {@code dias} días (60 por defecto) o nunca. */
    @GetMapping("/sin-rotacion")
    public ResponseEntity<?> sinRotacion(@RequestParam(defaultValue = "60") int dias,
                                         @RequestParam(required = false) Long establecimientoId,
                                         @RequestParam(defaultValue = "JSON") Formato formato) {
        return responder(reporteService.sinRotacion(dias, establecimientoId), formato);
    }

    /**
     * Todas las líneas de todos los comprobantes del periodo, con cliente, vendedor, prenda,
     * importes y pagos: el conjunto de datos para analizar (CSV por defecto; también XLSX).
     */
    @GetMapping("/detalle-ventas")
    public ResponseEntity<?> detalle(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(required = false) Long establecimientoId,
                                     @RequestParam(defaultValue = "CSV") Formato formato) {
        if (formato == Formato.JSON) {
            throw new BusinessException("El detalle de ventas se descarga como CSV o XLSX");
        }
        return responder(reporteService.detalle(periodo(desde, hasta, establecimientoId)), formato);
    }

    private Periodo periodo(LocalDate desde, LocalDate hasta, Long establecimientoId) {
        return reporteService.periodo(desde, hasta, establecimientoId);
    }

    private ResponseEntity<?> responder(Reporte<?> reporte, Formato formato) {
        return respuesta.responder(reporte, formato);
    }
}
