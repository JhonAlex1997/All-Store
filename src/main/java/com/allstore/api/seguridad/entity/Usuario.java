package com.allstore.api.seguridad.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
@Entity
@Table(name = "usuario")
public class Usuario extends BaseEntity {

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    @Column(nullable = false, length = 50)
    private String username;

    /** Hash BCrypt, nunca la contraseña en texto plano. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    /** Los tokens emitidos antes de este instante ya no valen. */
    @Column(name = "tokens_validos_desde", nullable = false)
    private OffsetDateTime tokensValidosDesde;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Column(nullable = false)
    private boolean activo = true;

    public boolean estaBloqueado(OffsetDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Suma un intento fallido y bloquea temporalmente al llegar al máximo. */
    public void registrarIntentoFallido(OffsetDateTime ahora) {
        intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS_FALLIDOS) {
            bloqueadoHasta = ahora.plus(DURACION_BLOQUEO);
            intentosFallidos = 0;
        }
    }

    public void registrarIngresoExitoso(OffsetDateTime ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimoAcceso = ahora;
    }

    /**
     * Invalida los tokens ya emitidos (cierra todas las sesiones abiertas). Se trunca a
     * segundos porque la fecha de emisión del token (iat) tiene precisión de segundos.
     */
    public void cerrarSesiones(OffsetDateTime ahora) {
        tokensValidosDesde = ahora.truncatedTo(ChronoUnit.SECONDS);
    }
}
