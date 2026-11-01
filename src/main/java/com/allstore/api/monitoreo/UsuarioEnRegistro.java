package com.allstore.api.monitoreo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pone en el log el usuario de la solicitud, una vez validado su token. Va dentro de la cadena de
 * seguridad (ver SecurityConfig); {@link RegistroSolicitudes} lo lee al terminar y limpia el MDC.
 */
public class UsuarioEnRegistro extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.isAuthenticated() && !(autenticacion instanceof AnonymousAuthenticationToken)) {
            MDC.put(RegistroSolicitudes.MDC_USUARIO, autenticacion.getName());
        }
        chain.doFilter(request, response);
    }
}
