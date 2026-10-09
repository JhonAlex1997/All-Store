package com.allstore.api.empresa.controller;

import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EmpresaResponse;
import com.allstore.api.empresa.service.EmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/empresa")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @GetMapping
    public EmpresaResponse obtener() {
        return empresaService.obtener();
    }

    /** Registra o actualiza los datos del emisor (hay una sola empresa). */
    @PutMapping
    public EmpresaResponse guardar(@Valid @RequestBody EmpresaRequest request) {
        return empresaService.guardar(request);
    }
}
