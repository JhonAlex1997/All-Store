package com.allstore.api.venta.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.ConvertirVentaRequest;
import com.allstore.api.venta.dto.FiltroVentas;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.NotaDebitoRequest;
import com.allstore.api.venta.dto.ReemitirRequest;
import com.allstore.api.venta.dto.TotalesVentasResponse;
import com.allstore.api.venta.dto.VentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.dto.VentaResumenResponse;
import com.allstore.api.venta.service.AnulacionService;
import com.allstore.api.venta.service.ConsultaVentasService;
import com.allstore.api.venta.service.NotaCreditoService;
import com.allstore.api.venta.service.NotaDebitoService;
import com.allstore.api.venta.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final ConsultaVentasService consultaVentasService;
    private final NotaCreditoService notaCreditoService;
    private final AnulacionService anulacionService;
    private final NotaDebitoService notaDebitoService;
    private final ImpresionService impresionService;

    /**
     * Ej: GET /api/ventas?desde=2026-10-01&hasta=2026-10-31&tipoComprobante=BOLETA,FACTURA&metodoPago=YAPE
     * (todos los filtros en {@link FiltroVentas}).
     */
    @GetMapping
    public PageResponse<VentaResumenResponse> listar(
            @ParameterObject FiltroVentas filtro,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pageable) {
        return consultaVentasService.listar(filtro, pageable);
    }

    /** Cantidades, montos y lo cobrado por método de las ventas que cumplen los mismos filtros del listado. */
    @GetMapping("/totales")
    public TotalesVentasResponse totales(@ParameterObject FiltroVentas filtro) {
        return consultaVentasService.totales(filtro);
    }

    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Long id) {
        return ventaService.obtenerDetalle(id);
    }

    /**
     * Representación impresa en PDF: formato TICKET (80 mm, impresora térmica) o A4. Se muestra en
     * el navegador (inline) para imprimir o descargar. Ej: GET /api/ventas/15/pdf?formato=TICKET
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(defaultValue = "A4") FormatoImpresion formato) {
        ImpresionService.Pdf pdf = impresionService.generar(id, formato);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombreArchivo()).build().toString())
                .body(pdf.contenido());
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
     * Comprobante nuevo (siguiente número) en lugar de uno que SUNAT rechazó, para la misma venta.
     * El cuerpo es opcional; ej. una factura rechazada por el RUC del cliente que pasa a boleta:
     * {"tipoComprobante": "BOLETA", "serieId": 2, "clienteId": 8}
     */
    @PostMapping("/{id}/reemitir")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse reemitir(@PathVariable Long id, @RequestBody(required = false) ReemitirRequest request) {
        return ventaService.reemitir(id, request);
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

    /**
     * Cobro adicional sobre una boleta o factura: intereses por mora, penalidad o aumento en el
     * valor. Ej. conceptos: [{"descripcion": "Penalidad por pago fuera de plazo", "monto": 20.00}]
     */
    @PostMapping("/{id}/notas-debito")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse emitirNotaDebito(@PathVariable Long id, @Valid @RequestBody NotaDebitoRequest request) {
        return notaDebitoService.emitir(id, request);
    }

    /**
     * Solo ADMIN (ver SecurityConfig). Revierte la mercadería y el cobro; si es una factura, boleta
     * o nota, además la da de baja ante SUNAT (dentro del plazo y si SUNAT ya la aceptó).
     */
    @PostMapping("/{id}/anular")
    public VentaResponse anular(@PathVariable Long id, @Valid @RequestBody AnularVentaRequest request) {
        return anulacionService.anular(id, request);
    }
}
