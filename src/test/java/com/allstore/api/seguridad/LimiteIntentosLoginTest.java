package com.allstore.api.seguridad;

import com.allstore.api.seguridad.config.LimiteIntentosLogin;
import com.allstore.api.seguridad.config.RespuestaErrorJson;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Password spraying: muchos usuarios distintos desde una misma IP. */
class LimiteIntentosLoginTest {

    private final RelojMovible reloj = new RelojMovible();
    private final LimiteIntentosLogin filtro = new LimiteIntentosLogin(3, Duration.ofMinutes(15), reloj,
            new RespuestaErrorJson(JsonMapper.builder().build()));
    /** Respuestas del login real: 401 credenciales incorrectas, 200 correcto. */
    private int respuestaDelLogin = 401;
    private final AtomicInteger llegaronAlLogin = new AtomicInteger();

    @Test
    void trasElMaximoDeFallosLaIpEsperaAunqueCadaIntentoSeaAOtroUsuario() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(ingresar("200.1.1.1").getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse bloqueada = ingresar("200.1.1.1");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isNotBlank();
        assertThat(bloqueada.getContentAsString()).contains("Demasiados intentos fallidos");
        // Ni siquiera llegó a revisar la contraseña (no gasta BCrypt).
        assertThat(llegaronAlLogin).hasValue(3);
        // Otra IP no se ve afectada, ni con la clave correcta.
        respuestaDelLogin = 200;
        assertThat(ingresar("200.2.2.2").getStatus()).isEqualTo(200);
        // Mientras dure el bloqueo, ni la clave correcta pasa desde la IP bloqueada.
        assertThat(ingresar("200.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void pasadaLaVentanaPuedeIntentarDeNuevo() throws Exception {
        for (int i = 0; i < 3; i++) {
            ingresar("200.1.1.1");
        }
        reloj.avanzar(Duration.ofMinutes(16));
        respuestaDelLogin = 200;
        assertThat(ingresar("200.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void losIngresosCorrectosNoCuentan() throws Exception {
        respuestaDelLogin = 200;
        for (int i = 0; i < 10; i++) {
            assertThat(ingresar("200.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void unaRutaCodificadaTambienCuenta() throws Exception {
        // "/api/auth/%6Cogin" es "/api/auth/login": Spring lo atiende igual, así que el límite también aplica.
        for (int i = 0; i < 3; i++) {
            assertThat(ingresar("200.3.3.3", "/api/auth/%6Cogin").getStatus()).isEqualTo(401);
        }
        assertThat(ingresar("200.3.3.3", "/api/auth/login").getStatus()).isEqualTo(429);
        assertThat(ingresar("200.3.3.3", "/api/auth/%6c%6fgin").getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse ingresar(String ip) throws Exception {
        return ingresar(ip, "/api/auth/login");
    }

    private MockHttpServletResponse ingresar(String ip, String ruta) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain login = (req, res) -> {
            llegaronAlLogin.incrementAndGet();
            ((MockHttpServletResponse) res).setStatus(respuestaDelLogin);
        };
        filtro.doFilter(request, response, login);
        return response;
    }

    private static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-10-29T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
