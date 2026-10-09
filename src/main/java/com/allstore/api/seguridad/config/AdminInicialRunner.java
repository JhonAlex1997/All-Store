package com.allstore.api.seguridad.config;

import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import com.allstore.api.seguridad.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * Sin usuarios no se puede entrar al sistema para crear el primero. En el primer arranque sobre
 * una base vacía se crea el usuario "admin", que debe cambiar su contraseña al ingresar.
 *
 * <p>La contraseña viene de ADMIN_PASSWORD_INICIAL; si no se definió, se genera una aleatoria y
 * se muestra UNA sola vez en el log. Corre después de los datos demo (que, en dev, ya crean sus
 * propios usuarios).
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class AdminInicialRunner implements ApplicationRunner {

    public static final String USERNAME = "admin";
    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final SeguridadProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        String password = properties.adminInicialPassword();
        boolean generada = !StringUtils.hasText(password);
        if (generada) {
            password = generarPassword();
        }
        usuarioService.registrar(USERNAME, "Administrador", Rol.ADMIN, password, true);
        if (generada) {
            log.warn("""

                    ==================================================================
                     Se creó el usuario administrador inicial.
                       usuario:    {}
                       contraseña: {}
                     Se muestra solo esta vez. Deberás cambiarla al primer ingreso.
                    ==================================================================""", USERNAME, password);
        } else {
            log.info("Se creó el usuario administrador inicial '{}' con la contraseña de ADMIN_PASSWORD_INICIAL", USERNAME);
        }
    }

    /** 16 caracteres sin ambiguos (0/O, 1/l/I), con al menos una letra y un número. */
    private static String generarPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }
        sb.append((char) ('a' + random.nextInt(26))).append(2 + random.nextInt(8));
        return sb.toString();
    }
}
