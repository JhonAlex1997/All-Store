package com.allstore.api.guia.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.guia.dto.GuiaRemisionRequest;
import com.allstore.api.guia.dto.GuiaRemisionResponse;
import com.allstore.api.guia.dto.GuiaRemisionResumenResponse;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.guia.service.GuiaRemisionService;
import com.allstore.api.impresion.ImpresionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/guias")
@RequiredArgsConstructor
public class GuiaRemisionController {

    private final GuiaRemisionService guiaService;
    private final ImpresionService impresionService;

    /**
     * Ej: GET /api/guias?motivo=TRASLADO_ESTABLECIMIENTOS&desde=2026-10-01&hasta=2026-10-31, o las
     * guías de una venta: GET /api/guias?comprobanteId=15
     */
    @GetMapping
    public PageResponse<GuiaRemisionResumenResponse> listar(
            @RequestParam(required = false) MotivoTraslado motivo,
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) Long comprobanteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pageable) {
        return guiaService.listar(motivo, establecimientoId, comprobanteId, desde, hasta, pageable);
    }

    @GetMapping("/{id}")
    public GuiaRemisionResponse obtener(@PathVariable Long id) {
        return guiaService.obtenerDetalle(id);
    }

    /**
     * Emite la guía. Traslado entre locales: mueve el stock del local de la serie al de destino.
     * Venta: entrega lo vendido (todo o una parte) a la dirección del cliente.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuiaRemisionResponse emitir(@Valid @RequestBody GuiaRemisionRequest request) {
        return guiaService.emitir(request);
    }

    /** Representación impresa en PDF (A4). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        ImpresionService.Pdf pdf = impresionService.generarGuia(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombreArchivo()).build().toString())
                .body(pdf.contenido());
    }
}
