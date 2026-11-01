package com.allstore.api.bitacora.controller;

import com.allstore.api.bitacora.dto.RegistroBitacoraResponse;
import com.allstore.api.bitacora.entity.AccionBitacora;
import com.allstore.api.bitacora.service.Bitacora;
import com.allstore.api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Bitácora de acciones sensibles (solo ADMIN, ver SecurityConfig). Solo lectura: nada se edita ni se borra. */
@RestController
@RequestMapping("/api/bitacora")
@RequiredArgsConstructor
public class BitacoraController {

    private final Bitacora bitacora;

    /**
     * Ej: GET /api/bitacora?desde=2026-11-01&usuario=jperez&accion=COMPROBANTE_ANULADO,DESCUENTO_EN_VENTA.
     * Para ver la historia de algo: ?entidad=COMPROBANTE&entidadId=152. Lo más reciente primero.
     */
    @GetMapping
    public PageResponse<RegistroBitacoraResponse> consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) List<AccionBitacora> accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Long entidadId,
            @ParameterObject @PageableDefault(size = 50, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return bitacora.consultar(desde, hasta, usuario, accion, entidad, entidadId, pageable);
    }
}
