package com.allstore.api.seguridad.service;

import com.allstore.api.seguridad.config.SeguridadProperties;
import com.allstore.api.seguridad.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Emite los tokens de sesión. La validación la hace Spring Security con el JwtDecoder. */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String ISSUER = "allstore-api";
    public static final String CLAIM_USUARIO_ID = "uid";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_NOMBRE = "nombre";

    private final JwtEncoder jwtEncoder;
    private final SeguridadProperties properties;

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion()))
                .claim(CLAIM_USUARIO_ID, usuario.getId())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .claim(CLAIM_NOMBRE, usuario.getNombre())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration duracion() {
        return Duration.ofMinutes(properties.expiracionMinutos());
    }
}
