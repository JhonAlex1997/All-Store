package com.allstore.api.compra.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.compra.dto.AnularCompraRequest;
import com.allstore.api.compra.dto.CompraRequest;
import com.allstore.api.compra.dto.CompraResponse;
import com.allstore.api.compra.service.CompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Compras a proveedores (ADMIN y ALMACENERO, ver SecurityConfig). */
@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraController {

    private final UnaVez unaVez;
    private final CompraService compraService;

    /** Ej: GET /api/compras?periodo=2026-11&proveedorId=3 */
    @GetMapping
    public PageResponse<CompraResponse> listar(@RequestParam(required = false) String periodo,
                                               @RequestParam(required = false) Long proveedorId,
                                               @ParameterObject @PageableDefault(size = 20, sort = "fechaEmision", direction = Sort.Direction.DESC) Pageable pageable) {
        return compraService.listar(periodo, proveedorId, pageable);
    }

    @GetMapping("/{id}")
    public CompraResponse obtener(@PathVariable Long id) {
        return compraService.obtenerDetalle(id);
    }

    /** Registra la compra: la mercadería y los insumos entran al stock del local. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponse registrar(@Valid @RequestBody CompraRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "registrar-compra", CompraResponse.class,
                () -> compraService.registrar(request), request);
    }

    /** Anula la compra y saca del stock lo que había entrado. */
    @PostMapping("/{id}/anular")
    public CompraResponse anular(@PathVariable Long id, @Valid @RequestBody AnularCompraRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "anular-compra", CompraResponse.class,
                () -> compraService.anular(id, request), id, request);
    }
}
