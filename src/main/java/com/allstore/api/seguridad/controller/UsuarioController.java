package com.allstore.api.seguridad.controller;

import com.allstore.api.seguridad.dto.ReiniciarPasswordRequest;
import com.allstore.api.seguridad.dto.UsuarioCreateRequest;
import com.allstore.api.seguridad.dto.UsuarioResponse;
import com.allstore.api.seguridad.dto.UsuarioUpdateRequest;
import com.allstore.api.seguridad.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioCreateRequest request) {
        return usuarioService.crear(request);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest request,
                                      Authentication auth) {
        return usuarioService.actualizar(id, request, auth.getName());
    }

    @PostMapping("/{id}/reiniciar-password")
    public UsuarioResponse reiniciarPassword(@PathVariable Long id, @Valid @RequestBody ReiniciarPasswordRequest request) {
        return usuarioService.reiniciarPassword(id, request);
    }

    @PostMapping("/{id}/desbloquear")
    public UsuarioResponse desbloquear(@PathVariable Long id) {
        return usuarioService.desbloquear(id);
    }
}
