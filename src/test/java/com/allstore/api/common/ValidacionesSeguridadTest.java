package com.allstore.api.common;

import com.allstore.api.common.validacion.BytesMaximos;
import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.config.ConfiguracionSegura;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validaciones contra phishing y errores internos, y la configuración mínima de producción. */
class ValidacionesSeguridadTest {

    private final TextoSeguro.Validador texto = new TextoSeguro.Validador();

    @Test
    void textoSeguroRechazaEnlacesHtmlYCaracteresDeControl() {
        assertThat(texto.isValid("Juan. Confirma tu pago en http://pago-tienda.xyz", null)).isFalse();
        assertThat(texto.isValid("Ingresa a WWW.premios.pe", null)).isFalse();
        assertThat(texto.isValid("<a href=x>Pagar</a>", null)).isFalse();
        assertThat(texto.isValid("Nombre\u0000oculto", null)).isFalse();
        assertThat(texto.isValid("ftp://archivo", null)).isFalse();
    }

    @Test
    void textoSeguroAceptaNombresYDireccionesReales() {
        assertThat(texto.isValid("María Peña Ñahui", null)).isTrue();
        assertThat(texto.isValid("DISTRIBUIDORA TEXTIL DEMO S.A.C.", null)).isTrue();
        assertThat(texto.isValid("Jr. Gamarra 1050, Int. 3 - La Victoria", null)).isTrue();
        assertThat(texto.isValid("Cambio de talla:\nla 28 le quedó chica", null)).isTrue();
        assertThat(texto.isValid(null, null)).isTrue();
    }

    @Test
    void bytesMaximosCuentaBytesNoCaracteres() {
        BytesMaximos.Validador validador = new BytesMaximos.Validador();
        validador.initialize(new BytesMaximos() {
            public int value() { return 72; }
            public String message() { return ""; }
            public Class<?>[] groups() { return new Class[0]; }
            @SuppressWarnings("unchecked")
            public Class<? extends jakarta.validation.Payload>[] payload() { return new Class[0]; }
            public Class<? extends java.lang.annotation.Annotation> annotationType() { return BytesMaximos.class; }
        });
        assertThat(validador.isValid("a".repeat(72), null)).isTrue();
        // 42 caracteres pero 82 bytes: BCrypt no los admite.
        assertThat(validador.isValid("a" + "ñ".repeat(40) + "1", null)).isFalse();
    }

    @Test
    void enProduccionNoArrancaConConfiguracionDeDesarrollo() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.demo.cargar-datos", "true")
                .withProperty("springdoc.swagger-ui.enabled", "true")
                .withProperty("app.entrega.correo.modo", "ARCHIVO")
                .withProperty("app.entrega.url-publica", "http://tienda.pe")
                .withProperty("app.seguridad.cors-origenes", "*");

        assertThatThrownBy(() -> new ConfiguracionSegura(env, sunat(EntornoSunat.PRODUCCION)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.demo.cargar-datos")
                .hasMessageContaining("Swagger")
                .hasMessageContaining("ARCHIVO")
                .hasMessageContaining("https://")
                .hasMessageContaining("CORS_ORIGENES");
        // En pruebas (beta) se permite.
        assertThatCode(() -> new ConfiguracionSegura(env, sunat(EntornoSunat.BETA))).doesNotThrowAnyException();
    }

    @Test
    void enProduccionArrancaConLaConfiguracionCorrecta() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.entrega.url-publica", "https://comprobantes.tienda.pe")
                .withProperty("app.seguridad.cors-origenes", "https://caja.tienda.pe");

        assertThatCode(() -> new ConfiguracionSegura(env, sunat(EntornoSunat.PRODUCCION))).doesNotThrowAnyException();
    }

    private static SunatProperties sunat(EntornoSunat entorno) {
        return new SunatProperties(true, entorno, null, null, "USUARIO", "clave", null, null, 30000, 8, null);
    }
}
