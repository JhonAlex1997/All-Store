package com.allstore.api.seguridad.service;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.seguridad.entity.Rol;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Quién hace la operación, para lo que depende del rol dentro de un mismo endpoint. */
@Component
public class UsuarioEnSesion {

    private static final String AUTORIDAD_ADMIN = "ROLE_" + Rol.ADMIN.name();

    /** El username de la sesión; "sistema" en los procesos sin usuario (arranque, tareas programadas). */
    public String nombre() {
        Authentication auth = autenticado();
        return auth == null ? JpaAuditingConfig.USUARIO_SISTEMA : auth.getName();
    }

    /**
     * Ve la información de todo el negocio: el administrador y los procesos internos (sin sesión).
     * Los endpoints de la API siempre tienen sesión: la regla de acceso la exige antes de llegar aquí.
     */
    public boolean veTodo() {
        Authentication auth = autenticado();
        return auth == null || auth.getAuthorities().stream().anyMatch(a -> AUTORIDAD_ADMIN.equals(a.getAuthority()));
    }

    private static Authentication autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken ? null : auth;
    }
}
