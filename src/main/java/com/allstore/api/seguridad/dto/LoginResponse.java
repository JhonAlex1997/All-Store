package com.allstore.api.seguridad.dto;

/**
 * @param accessToken token a enviar en cada petición: {@code Authorization: Bearer <token>}
 * @param expiraEnSegundos segundos hasta que el token vence y hay que volver a ingresar
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiraEnSegundos,
        UsuarioResponse usuario
) {
}
