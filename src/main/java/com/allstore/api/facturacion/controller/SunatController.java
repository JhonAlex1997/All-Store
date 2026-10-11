package com.allstore.api.facturacion.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.facturacion.dto.EnvioSunatResponse;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.service.EnvioSunatService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Panel de facturación electrónica (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/sunat")
@RequiredArgsConstructor
public class SunatController {

    private final EnvioSunatService envioSunatService;

    /** Ej: GET /api/sunat/envios?estado=PENDIENTE */
    @GetMapping("/envios")
    public PageResponse<EnvioSunatResponse> listar(
            @RequestParam(required = false) EstadoSunat estado,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return envioSunatService.listar(estado, pageable);
    }

    @GetMapping("/comprobantes/{comprobanteId}")
    public EnvioSunatResponse consultar(@PathVariable Long comprobanteId) {
        return envioSunatService.consultar(comprobanteId);
    }

    /**
     * Envía ahora, sin esperar al proceso programado (también reintenta uno en ERROR). Con
     * regenerarXml=true vuelve a generar el XML (cambia el hash: hay que reimprimir).
     */
    @PostMapping("/comprobantes/{comprobanteId}/enviar")
    public EnvioSunatResponse enviar(@PathVariable Long comprobanteId,
                                     @RequestParam(defaultValue = "false") boolean regenerarXml) {
        return envioSunatService.enviarAhora(comprobanteId, regenerarXml);
    }

    @GetMapping("/comprobantes/{comprobanteId}/xml")
    public ResponseEntity<byte[]> xml(@PathVariable Long comprobanteId) {
        return descarga(envioSunatService.xml(comprobanteId), MediaType.APPLICATION_XML);
    }

    /** Constancia de recepción de SUNAT (zip). */
    @GetMapping("/comprobantes/{comprobanteId}/cdr")
    public ResponseEntity<byte[]> cdr(@PathVariable Long comprobanteId) {
        return descarga(envioSunatService.cdr(comprobanteId), MediaType.parseMediaType("application/zip"));
    }

    private static ResponseEntity<byte[]> descarga(EnvioSunatService.Archivo archivo, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
