package com.allstore.api.seguridad.service;

import com.allstore.api.common.exception.AutenticacionException;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.seguridad.dto.CambiarPasswordRequest;
import com.allstore.api.seguridad.dto.LoginRequest;
import com.allstore.api.seguridad.dto.LoginResponse;
import com.allstore.api.seguridad.dto.UsuarioResponse;
import com.allstore.api.seguridad.entity.Usuario;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private static final String CREDENCIALES_INCORRECTAS = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /**
     * Hash contra el que se compara cuando el usuario no existe, para que la respuesta tarde lo
     * mismo que con un usuario real: así no se puede averiguar qué usuarios existen midiendo
     * el tiempo de respuesta.
     */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashFicticio = passwordEncoder.encode("hash-ficticio-para-igualar-tiempos");
    }

    /**
     * Los intentos fallidos se guardan aunque el login falle (noRollbackFor): sin eso, el
     * contador de bloqueo nunca subiría.
     */
    @Transactional(noRollbackFor = AutenticacionException.class)
    public LoginResponse login(LoginRequest request) {
        OffsetDateTime ahora = OffsetDateTime.now();
        Optional<Usuario> encontrado = usuarioRepository.findByUsernameIgnoreCase(request.username().trim());
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(request.password(), hashFicticio);
            throw new AutenticacionException(CREDENCIALES_INCORRECTAS);
        }
        Usuario usuario = encontrado.get();
        if (usuario.estaBloqueado(ahora)) {
            throw new AutenticacionException(mensajeBloqueo(usuario, ahora));
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            usuario.registrarIntentoFallido(ahora);
            if (usuario.estaBloqueado(ahora)) {
                log.warn("Usuario {} bloqueado por {} intentos fallidos", usuario.getUsername(),
                        Usuario.MAX_INTENTOS_FALLIDOS);
                throw new AutenticacionException(mensajeBloqueo(usuario, ahora));
            }
            throw new AutenticacionException(CREDENCIALES_INCORRECTAS);
        }
        // El estado se revela solo a quien conoce la contraseña.
        if (!usuario.isActivo()) {
            throw new AutenticacionException("Tu usuario está desactivado. Consulta con un administrador.");
        }
        usuario.registrarIngresoExitoso(ahora);
        return emitirSesion(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse perfil(String username) {
        return UsuarioResponse.from(obtener(username));
    }

    /**
     * Cambia la contraseña y cierra todas las sesiones abiertas del usuario (en otros equipos
     * también). Devuelve una sesión nueva para que quien la cambió siga trabajando.
     */
    @Transactional
    public LoginResponse cambiarPassword(String username, CambiarPasswordRequest request) {
        Usuario usuario = obtener(username);
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new BusinessException("La contraseña actual no es correcta");
        }
        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPasswordHash())) {
            throw new BusinessException("La nueva contraseña debe ser distinta de la actual");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        usuario.setDebeCambiarPassword(false);
        usuario.cerrarSesiones(OffsetDateTime.now());
        return emitirSesion(usuario);
    }

    private LoginResponse emitirSesion(Usuario usuario) {
        return new LoginResponse(jwtService.generarToken(usuario), "Bearer", jwtService.duracion().toSeconds(),
                UsuarioResponse.from(usuario));
    }

    private Usuario obtener(String username) {
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", username));
    }

    private static String mensajeBloqueo(Usuario usuario, OffsetDateTime ahora) {
        long minutos = Math.max(1, Duration.between(ahora, usuario.getBloqueadoHasta()).toMinutes() + 1);
        return "Usuario bloqueado por varios intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s).";
    }
}
