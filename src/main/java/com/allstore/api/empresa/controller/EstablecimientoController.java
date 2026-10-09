package com.allstore.api.empresa.controller;

import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.EstablecimientoResponse;
import com.allstore.api.empresa.service.EstablecimientoService;
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
@RequestMapping("/api/establecimientos")
@RequiredArgsConstructor
public class EstablecimientoController {

    private final EstablecimientoService establecimientoService;

    @GetMapping
    public List<EstablecimientoResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return establecimientoService.listar(soloActivos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstablecimientoResponse crear(@Valid @RequestBody EstablecimientoRequest request) {
        return establecimientoService.crear(request);
    }

    @PutMapping("/{id}")
    public EstablecimientoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EstablecimientoRequest request) {
        return establecimientoService.actualizar(id, request);
    }
}
