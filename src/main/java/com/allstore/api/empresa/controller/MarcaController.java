package com.allstore.api.empresa.controller;

import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.dto.MarcaResponse;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.LogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Nombre y logo de la tienda para la pantalla de login y el menú. Es público porque se muestra antes
 * de iniciar sesión; solo expone el nombre y el logo, que igual van impresos en cada comprobante.
 */
@RestController
@RequestMapping("/api/publico/marca")
@RequiredArgsConstructor
public class MarcaController {

    private final EmpresaService empresaService;
    private final LogoService logoService;

    @GetMapping
    public ResponseEntity<MarcaResponse> marca() {
        return ResponseEntity.ok()
                // Sin caché: si el administrador cambia el nombre comercial, se ve al recargar.
                .cacheControl(CacheControl.noCache())
                .body(empresaService.marca());
    }

    /**
     * El logo en PNG. La URL lleva la versión (?v=, de la marca) y cambia con cada logo nuevo, por eso
     * el navegador puede guardarlo un año sin volver a pedirlo.
     */
    @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> logo(@RequestParam(name = "v", required = false) Long version) {
        LogoService.Logo logo = logoService.obtener()
                .orElseThrow(() -> new ResourceNotFoundException("La tienda no tiene logo"));
        CacheControl cache = version != null && version == logo.version()
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        return ResponseEntity.ok()
                .cacheControl(cache)
                .contentType(MediaType.IMAGE_PNG)
                .body(logo.png());
    }
}
