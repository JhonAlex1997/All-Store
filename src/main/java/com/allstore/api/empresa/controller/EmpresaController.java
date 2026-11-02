package com.allstore.api.empresa.controller;

import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EmpresaResponse;
import com.allstore.api.empresa.dto.MarcaResponse;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.LogoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/empresa")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;
    private final LogoService logoService;

    @GetMapping
    public EmpresaResponse obtener() {
        return empresaService.obtener();
    }

    /** Registra o actualiza los datos del emisor (hay una sola empresa). */
    @PutMapping
    public EmpresaResponse guardar(@Valid @RequestBody EmpresaRequest request) {
        return empresaService.guardar(request);
    }

    /**
     * Sube o reemplaza el logo (PNG o JPG, máximo 1 MB). Se muestra en el login, el menú y los
     * comprobantes. Conviene PNG con fondo transparente. Devuelve la marca con la nueva versión del logo.
     */
    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MarcaResponse subirLogo(@RequestPart("archivo") MultipartFile archivo) {
        logoService.guardar(archivo);
        return empresaService.marca();
    }

    @DeleteMapping("/logo")
    public ResponseEntity<Void> quitarLogo() {
        logoService.eliminar();
        return ResponseEntity.noContent().build();
    }
}
