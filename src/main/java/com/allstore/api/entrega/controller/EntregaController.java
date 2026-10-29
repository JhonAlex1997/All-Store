package com.allstore.api.entrega.controller;

import com.allstore.api.entrega.dto.EntregaResponse;
import com.allstore.api.entrega.dto.EnviarCorreoRequest;
import com.allstore.api.entrega.dto.EnviarWhatsappRequest;
import com.allstore.api.entrega.dto.EnvioCorreoResponse;
import com.allstore.api.entrega.dto.EnvioWhatsappResponse;
import com.allstore.api.entrega.service.EntregaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entregar un comprobante al cliente (caja y administrador, como el resto de /api/ventas). */
@RestController
@RequestMapping("/api/ventas/{id}/entrega")
@RequiredArgsConstructor
public class EntregaController {

    private final EntregaService entregaService;

    /** Enlaces de descarga para compartir, WhatsApp y los correos ya enviados. */
    @GetMapping
    public EntregaResponse entrega(@PathVariable Long id) {
        return entregaService.entrega(id);
    }

    /**
     * Envía el comprobante por correo (PDF, XML y constancia de SUNAT). Sin cuerpo, o sin email, va
     * al correo registrado del cliente. Ej: {"email": "cliente@correo.com"}
     */
    /**
     * Envía el comprobante por WhatsApp (con la API de Meta si está configurada; si no, devuelve
     * el enlace wa.me para enviarlo a mano). Cuerpo opcional: {"celular": "987654321"}.
     */
    @PostMapping("/whatsapp")
    public EnvioWhatsappResponse enviarWhatsapp(@PathVariable Long id, @Valid @RequestBody(required = false) EnviarWhatsappRequest request) {
        return entregaService.enviarWhatsapp(id, request == null ? null : request.celular());
    }

    @PostMapping("/correo")
    public EnvioCorreoResponse enviarCorreo(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) EnviarCorreoRequest request) {
        return entregaService.enviarCorreo(id, request == null ? null : request.email());
    }
}
