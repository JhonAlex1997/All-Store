package com.allstore.api.empresa.controller;

import com.allstore.api.empresa.dto.MarcaResponse;
import com.allstore.api.empresa.service.EmpresaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Nombre de la tienda para la pantalla de login y el título del sistema. Es público porque se muestra
 * antes de iniciar sesión; solo expone el nombre, que igual va impreso en cada comprobante.
 */
@RestController
@RequestMapping("/api/publico/marca")
@RequiredArgsConstructor
public class MarcaController {

    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<MarcaResponse> marca() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(empresaService.marca());
    }
}
