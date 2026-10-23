package com.allstore.api.alerta.controller;

import com.allstore.api.alerta.dto.AlertasResponse;
import com.allstore.api.alerta.service.AlertaService;
import com.allstore.api.alerta.service.AvisoAlertas;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertaService;
    private final AvisoAlertas avisoAlertas;

    /** Lo que hay que atender ahora: para el panel (ej. un contador en el menú). */
    @GetMapping
    public AlertasResponse alertas() {
        return alertaService.alertas();
    }

    /** Envía ahora el resumen por correo (para probar la configuración); sin alertas no envía nada. */
    @PostMapping("/correo")
    public AvisoAlertas.Aviso enviarCorreo() {
        return avisoAlertas.enviar();
    }
}
