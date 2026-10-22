package com.allstore.api.entrega;

import com.allstore.api.entrega.config.EntregaProperties;
import com.allstore.api.entrega.config.ModoCorreo;
import com.allstore.api.entrega.service.ServicioCorreo;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Transporte según el modo: SMTP entrega el mensaje al servidor de correo; sin servidor, avisa qué falta. */
class ServicioCorreoTest {

    @Test
    @SuppressWarnings("unchecked")
    void enModoSmtpEntregaElMensajeAlServidorDeCorreo() throws Exception {
        JavaMailSender servidor = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(servidor);
        ServicioCorreo correo = new ServicioCorreo(propiedades(ModoCorreo.SMTP), proveedor);
        MimeMessage mensaje = correo.nuevoMensaje();

        correo.enviar(mensaje, "B001-00000001-1");

        verify(servidor).send(mensaje);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enModoSmtpSinServidorConfiguradoExplicaQueFalta() {
        ObjectProvider<JavaMailSender> sinServidor = mock(ObjectProvider.class);
        ServicioCorreo correo = new ServicioCorreo(propiedades(ModoCorreo.SMTP), sinServidor);

        assertThatThrownBy(() -> correo.enviar(correo.nuevoMensaje(), "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST");
    }

    private static EntregaProperties propiedades(ModoCorreo modo) {
        return new EntregaProperties("http://localhost:8080", new EntregaProperties.Correo(modo, null, "target/correos-test", true, 5));
    }
}
