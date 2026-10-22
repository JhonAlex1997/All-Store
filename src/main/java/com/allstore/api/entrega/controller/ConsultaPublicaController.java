package com.allstore.api.entrega.controller;

import com.allstore.api.entrega.service.EntregaService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.impresion.FormatoImpresion;
import com.allstore.api.impresion.ImpresionService;
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

/**
 * Descarga pública del comprobante, sin iniciar sesión (ver SecurityConfig): es el enlace que
 * recibe el cliente por correo o WhatsApp. El código es aleatorio (UUID), así que no se puede
 * adivinar ni recorrer los comprobantes de otros.
 */
@RestController
@RequestMapping("/api/publico/comprobantes/{codigo}")
@RequiredArgsConstructor
public class ConsultaPublicaController {

    /** Que los buscadores no indexen comprobantes aunque alguien publique el enlace. */
    private static final String SIN_INDEXAR = "noindex, nofollow";

    private final EntregaService entregaService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID codigo, @RequestParam(defaultValue = "A4") FormatoImpresion formato) {
        ImpresionService.Pdf pdf = entregaService.pdfPublico(codigo, formato);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nombreArchivo()).build().toString())
                .header("X-Robots-Tag", SIN_INDEXAR)
                .body(pdf.contenido());
    }

    @GetMapping("/xml")
    public ResponseEntity<byte[]> xml(@PathVariable UUID codigo) {
        return descarga(entregaService.xmlPublico(codigo), MediaType.APPLICATION_XML);
    }

    /** Constancia de recepción de SUNAT (zip). */
    @GetMapping("/cdr")
    public ResponseEntity<byte[]> cdr(@PathVariable UUID codigo) {
        return descarga(entregaService.cdrPublico(codigo), MediaType.parseMediaType("application/zip"));
    }

    private static ResponseEntity<byte[]> descarga(EnvioSunatService.Archivo archivo, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .header("X-Robots-Tag", SIN_INDEXAR)
                .body(archivo.contenido());
    }
}
