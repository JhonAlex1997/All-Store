package com.allstore.api.monitoreo;

import com.allstore.api.monitoreo.dto.ErrorRegistrado;
import com.allstore.api.monitoreo.dto.EstadoSistemaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Monitoreo para el panel del administrador (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/sistema")
@RequiredArgsConstructor
public class SistemaController {

    private final EstadoSistemaService estadoSistemaService;
    private final RegistroErrores registroErrores;

    /** Base de datos, disco, memoria, procesos de envío, colas y errores recientes, con un estado general. */
    @GetMapping("/estado")
    public EstadoSistemaResponse estado() {
        return estadoSistemaService.estado();
    }

    /** Los últimos errores internos (hasta 100), del más reciente al más antiguo; el detalle está en el log. */
    @GetMapping("/errores")
    public List<ErrorRegistrado> errores() {
        return registroErrores.recientes();
    }
}
