package com.allstore.api.seguridad.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.seguridad.dto.ReiniciarPasswordRequest;
import com.allstore.api.seguridad.dto.UsuarioCreateRequest;
import com.allstore.api.seguridad.dto.UsuarioResponse;
import com.allstore.api.seguridad.dto.UsuarioUpdateRequest;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.entity.Usuario;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** Administración de usuarios (solo para el rol ADMIN). */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByUsernameAsc().stream().map(UsuarioResponse::from).toList();
    }

    @Transactional
    public UsuarioResponse crear(UsuarioCreateRequest request) {
        Usuario usuario = registrar(request.username(), request.nombre(), request.rol(), request.passwordTemporal(), true);
        return UsuarioResponse.from(usuario);
    }

    /**
     * Crea un usuario. Lo usan la API (contraseña temporal, debe cambiarla) y los procesos de
     * arranque (administrador inicial, usuarios demo).
     */
    @Transactional
    public Usuario registrar(String username, String nombre, Rol rol, String password, boolean debeCambiarPassword) {
        String usernameLimpio = username.trim().toLowerCase();
        if (usuarioRepository.existsByUsernameIgnoreCase(usernameLimpio)) {
            throw new BusinessException("Ya existe el usuario " + usernameLimpio);
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(usernameLimpio);
        usuario.setNombre(nombre.trim());
        usuario.setRol(rol);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setDebeCambiarPassword(debeCambiarPassword);
        usuario.cerrarSesiones(OffsetDateTime.now());
        return usuarioRepository.save(usuario);
    }

    /**
     * Cambiar el rol o desactivar cierra las sesiones abiertas del usuario, para que el cambio
     * aplique de inmediato y no recién cuando venza su token.
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request, String usernameActual) {
        Usuario usuario = obtener(id);
        boolean pierdeAdmin = usuario.getRol() == Rol.ADMIN && usuario.isActivo()
                && (request.rol() != Rol.ADMIN || !request.activo());
        if (pierdeAdmin) {
            if (usuario.getUsername().equalsIgnoreCase(usernameActual)) {
                throw new BusinessException("No puedes quitarte el rol de administrador ni desactivarte a ti mismo");
            }
            if (contarAdministradoresActivos() <= 1) {
                throw new BusinessException("Debe quedar al menos un administrador activo");
            }
        }
        boolean cambiaAcceso = usuario.getRol() != request.rol() || usuario.isActivo() != request.activo();
        usuario.setNombre(request.nombre().trim());
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());
        if (cambiaAcceso) {
            usuario.cerrarSesiones(OffsetDateTime.now());
        }
        return UsuarioResponse.from(usuario);
    }

    /** Para quien olvidó su contraseña: queda una temporal, debe cambiarla y se le quita el bloqueo. */
    @Transactional
    public UsuarioResponse reiniciarPassword(Long id, ReiniciarPasswordRequest request) {
        Usuario usuario = obtener(id);
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordTemporal()));
        usuario.setDebeCambiarPassword(true);
        quitarBloqueo(usuario);
        usuario.cerrarSesiones(OffsetDateTime.now());
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public UsuarioResponse desbloquear(Long id) {
        Usuario usuario = obtener(id);
        quitarBloqueo(usuario);
        return UsuarioResponse.from(usuario);
    }

    private static void quitarBloqueo(Usuario usuario) {
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
    }

    private long contarAdministradoresActivos() {
        return usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN);
    }

    private Usuario obtener(Long id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
}
