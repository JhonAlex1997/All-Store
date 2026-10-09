package com.allstore.api.seguridad.controller;

import com.allstore.api.seguridad.dto.CambiarPasswordRequest;
import com.allstore.api.seguridad.dto.LoginRequest;
import com.allstore.api.seguridad.dto.LoginResponse;
import com.allstore.api.seguridad.dto.UsuarioResponse;
import com.allstore.api.seguridad.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Devuelve el token que se envía luego en cada petición: Authorization: Bearer <token>. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Datos del usuario de la sesión actual. */
    @GetMapping("/me")
    public UsuarioResponse perfil(Authentication auth) {
        return authService.perfil(auth.getName());
    }

    /** Devuelve un token nuevo: el anterior deja de valer al cambiar la contraseña. */
    @PostMapping("/cambiar-password")
    public LoginResponse cambiarPassword(Authentication auth, @Valid @RequestBody CambiarPasswordRequest request) {
        return authService.cambiarPassword(auth.getName(), request);
    }
}
