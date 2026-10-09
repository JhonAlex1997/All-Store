package com.allstore.api.empresa.controller;

import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.dto.SerieResponse;
import com.allstore.api.empresa.dto.SerieUpdateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.SerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class SerieController {

    private final SerieService serieService;

    /** Ej: GET /api/series?tipoComprobante=BOLETA&establecimientoId=1 */
    @GetMapping
    public List<SerieResponse> listar(@RequestParam(required = false) TipoComprobante tipoComprobante,
                                      @RequestParam(required = false) Long establecimientoId) {
        return serieService.listar(tipoComprobante, establecimientoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SerieResponse crear(@Valid @RequestBody SerieCreateRequest request) {
        return serieService.crear(request);
    }

    @PutMapping("/{id}")
    public SerieResponse actualizar(@PathVariable Long id, @Valid @RequestBody SerieUpdateRequest request) {
        return serieService.actualizar(id, request);
    }
}
