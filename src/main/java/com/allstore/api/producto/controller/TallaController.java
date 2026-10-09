package com.allstore.api.producto.controller;

import com.allstore.api.producto.dto.TallaRequest;
import com.allstore.api.producto.dto.TallaResponse;
import com.allstore.api.producto.service.TallaService;
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
@RequestMapping("/api/tallas")
@RequiredArgsConstructor
public class TallaController {

    private final TallaService tallaService;

    @GetMapping
    public List<TallaResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivas) {
        return tallaService.listar(soloActivas);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TallaResponse crear(@Valid @RequestBody TallaRequest request) {
        return tallaService.crear(request);
    }

    @PutMapping("/{id}")
    public TallaResponse actualizar(@PathVariable Long id, @Valid @RequestBody TallaRequest request) {
        return tallaService.actualizar(id, request);
    }
}
