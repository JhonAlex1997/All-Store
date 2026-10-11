package com.allstore.api.venta.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.dto.VentaResumenResponse;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.service.NotaCreditoService;
import com.allstore.api.venta.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final NotaCreditoService notaCreditoService;

    /** Ej: GET /api/ventas?desde=2026-10-01&hasta=2026-10-31&tipoComprobante=BOLETA&busqueda=B001-15 */
    @GetMapping
    public PageResponse<VentaResumenResponse> listar(
            @RequestParam(required = false) TipoComprobante tipoComprobante,
            @RequestParam(required = false) EstadoComprobante estado,
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String busqueda,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pageable) {
        return ventaService.listar(tipoComprobante, estado, establecimientoId, desde, hasta, busqueda, pageable);
    }

    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Long id) {
        return ventaService.obtenerDetalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse crear(@Valid @RequestBody VentaRequest request) {
        return ventaService.crear(request);
    }

    /** Nota de venta → boleta o factura. */
    @PostMapping("/{id}/convertir")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse convertir(@PathVariable Long id, @Valid @RequestBody ConvertirVentaRequest request) {
        return ventaService.convertir(id, request);
    }

    /**
     * Devolución o anulación de una boleta o factura. Ej. cambio de talla: nota de crédito por
     * la prenda devuelta (DEVOLUCION_POR_ITEM) y una venta nueva por la prenda que se lleva.
     */
    @PostMapping("/{id}/notas-credito")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse emitirNotaCredito(@PathVariable Long id, @Valid @RequestBody NotaCreditoRequest request) {
        return notaCreditoService.emitir(id, request);
    }

    /** Solo notas de venta, solo ADMIN (ver SecurityConfig). Devuelve la mercadería al stock. */
    @PostMapping("/{id}/anular")
    public VentaResponse anular(@PathVariable Long id, @Valid @RequestBody AnularVentaRequest request) {
        return ventaService.anular(id, request);
    }
}
