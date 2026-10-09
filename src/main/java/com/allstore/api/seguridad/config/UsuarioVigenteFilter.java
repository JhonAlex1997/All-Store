package com.allstore.api.seguridad.config;

import com.allstore.api.seguridad.entity.Usuario;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import com.allstore.api.seguridad.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Un JWT vale por sí solo hasta que vence. Este filtro agrega lo que el token no sabe, mirando
 * el estado actual del usuario en la base:
 * <ul>
 *   <li>si fue desactivado, o cambió su contraseña o su rol después de emitido el token, la
 *       sesión se cierra (401);</li>
 *   <li>si debe cambiar su contraseña, solo puede ver su perfil y cambiarla (403).</li>
 * </ul>
 * No es un @Component para que Spring no lo registre dos veces; lo crea SecurityConfig.
 */
@RequiredArgsConstructor
public class UsuarioVigenteFilter extends OncePerRequestFilter {

    static final Set<String> RUTAS_PERMITIDAS_SIN_CAMBIAR_PASSWORD =
            Set.of("/api/auth/me", "/api/auth/cambiar-password");

    private final UsuarioRepository usuarioRepository;
    private final RespuestaErrorJson respuestaErrorJson;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuth.getToken();
        Optional<Usuario> usuario = Optional.ofNullable(jwt.<Number>getClaim(JwtService.CLAIM_USUARIO_ID))
                .map(Number::longValue)
                .flatMap(usuarioRepository::findById);

        if (usuario.isEmpty() || !usuario.get().isActivo() || emitidoAntesDelCierre(jwt, usuario.get())) {
            SecurityContextHolder.clearContext();
            respuestaErrorJson.escribir(request, response, HttpStatus.UNAUTHORIZED,
                    "Tu sesión ya no es válida, vuelve a iniciar sesión");
            return;
        }
        if (usuario.get().isDebeCambiarPassword()
                && !RUTAS_PERMITIDAS_SIN_CAMBIAR_PASSWORD.contains(request.getRequestURI())) {
            respuestaErrorJson.escribir(request, response, HttpStatus.FORBIDDEN,
                    "Debes cambiar tu contraseña antes de continuar");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean emitidoAntesDelCierre(Jwt jwt, Usuario usuario) {
        Instant emitido = jwt.getIssuedAt();
        return emitido == null || emitido.isBefore(usuario.getTokensValidosDesde().toInstant());
    }
}
