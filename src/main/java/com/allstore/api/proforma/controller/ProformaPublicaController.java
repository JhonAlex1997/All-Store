package com.allstore.api.proforma.controller;

import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
import com.allstore.api.proforma.service.ProformaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PDF de la proforma para el enlace que se comparte por WhatsApp o correo (sin sesión, por su código). */
@RestController
@RequestMapping("/api/publico/proformas/{codigo}")
@RequiredArgsConstructor
public class ProformaPublicaController {

    private final ProformaService proformaService;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID codigo, @RequestParam(defaultValue = "A4") FormatoImpresion formato) {
        ImpresionService.Pdf pdf = proformaService.pdfPublico(codigo, formato);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombreArchivo()).build().toString())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(pdf.contenido());
    }
}
