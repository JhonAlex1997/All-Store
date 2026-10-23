package com.allstore.api.venta;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.entrega.entity.EstadoCorreo;
import com.allstore.api.entrega.repository.EnvioCorreoRepository;
import com.allstore.api.entrega.service.EntregaService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.repository.ComprobanteRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrega al cliente: correo con adjuntos (en pruebas se guarda como .eml en target/correos-test),
 * envío automático al aceptar SUNAT, enlaces y descarga pública sin sesión.
 */
class EntregaIntegrationTest extends TiendaDePrueba {

    private static final Path CARPETA = Path.of("target/correos-test");

    @Autowired private EntregaService entregaService;
    @Autowired private EnvioCorreoRepository envioCorreoRepository;
    @Autowired private ComprobanteRepository comprobanteRepository;
    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EmpresaService empresaService;
    @Autowired private WebApplicationContext context;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    @BeforeEach
    void preparar() throws Exception {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Jeans Prueba",
                    "Jr. Prueba 1", "150115", "014567890", "ventas@prueba.pe", null));
        }
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(
                new ServicioEnvioSunat.Respuesta(true, "0", "Aceptada", List.of(), new byte[]{0x50, 0x4b}));
    }

    @Test
    void enviaElComprobanteConPdfXmlConstanciaYEnlace() throws Exception {
        VentaResponse factura = aceptada(vender(TipoComprobante.FACTURA, serieFactura, clienteRuc,
                List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null))));

        var envio = entregaService.enviarCorreo(factura.id(), "Compras@Empresa.pe");

        assertThat(envio.estado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(envio.destinatario()).isEqualTo("compras@empresa.pe");
        MimeMessage correo = leer(factura.numero() + "-" + envio.id());
        assertThat(correo.getSubject()).isEqualTo("Factura F991-00000001 - Jeans Prueba");
        assertThat(correo.getAllRecipients()[0].toString()).isEqualTo("compras@empresa.pe");
        assertThat(adjuntos(correo)).containsExactly("F991-00000001.pdf", "20000000001-01-F991-1.xml",
                "R-20000000001-01-F991-1.zip");
        UUID codigo = comprobanteRepository.findById(factura.id()).orElseThrow().getCodigoConsulta();
        assertThat(texto(correo)).contains("Hola, EMPRESA PRUEBA SAC:", "S/ 89.90",
                "http://localhost:8080/api/publico/comprobantes/" + codigo + "/pdf");
        assertThat(entregaService.entrega(factura.id()).correos()).singleElement()
                .satisfies(c -> assertThat(c.pedidoPor()).isEqualTo("sistema"));
    }

    @Test
    void sinCorreoDelClienteHayQueIndicarlo() {
        VentaResponse boleta = vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        assertThatThrownBy(() -> entregaService.enviarCorreo(boleta.id(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene correo registrado");
    }

    @Test
    void loAnuladoNoSeEntrega() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));
        anulacionService.anular(nota.id(), new AnularVentaRequest("prueba"));

        assertThatThrownBy(() -> entregaService.enviarCorreo(nota.id(), "a@b.pe"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("está anulado");
    }

    @Test
    void alAceptarSunatSeEncolaElCorreoSoloSiElClienteTieneCorreo() {
        Long conCorreo = clienteService.crear(new ClienteRequest(TipoDocumentoIdentidad.DNI, "45678912", "Rosa Prueba",
                null, null, "987 654 321", "rosa@correo.pe", true)).id();
        VentaResponse suya = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, conCorreo, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null))));
        VentaResponse sinCorreo = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, clienteDni, List.of(item(jeanB, 1, null)),
                List.of(efectivo("89.90", null))));

        assertThat(envioCorreoRepository.findByComprobanteIdOrderByIdDesc(suya.id())).singleElement().satisfies(e -> {
            assertThat(e.isAutomatico()).isTrue();
            assertThat(e.getDestinatario()).isEqualTo("rosa@correo.pe");
            assertThat(e.getEstado()).isEqualTo(EstadoCorreo.PENDIENTE);
        });
        assertThat(envioCorreoRepository.findByComprobanteIdOrderByIdDesc(sinCorreo.id())).isEmpty();

        var entrega = entregaService.entrega(suya.id());
        assertThat(entrega.whatsapp()).startsWith("https://wa.me/51987654321?text=Hola%2C%20te%20enviamos%20tu%20boleta");
        assertThat(entrega.enlaces().xml()).endsWith("/xml");
        assertThat(entrega.enlaces().cdr()).endsWith("/cdr");
        assertThat(entrega.correoCliente()).isEqualTo("rosa@correo.pe");
    }

    @Test
    void laNotaDeVentaSoloTieneEnlaceDelPdfYWhatsappSinNumero() {
        VentaResponse nota = vender(TipoComprobante.NOTA_VENTA, serieNotaVenta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null)));

        var entrega = entregaService.entrega(nota.id());

        assertThat(entrega.enlaces().pdf()).contains("/api/publico/comprobantes/");
        assertThat(entrega.enlaces().xml()).isNull();
        assertThat(entrega.whatsapp()).startsWith("https://wa.me/?text=");
    }

    @Test
    void elClienteDescargaSinIniciarSesionConElCodigoDelEnlace() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        VentaResponse boleta = aceptada(vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)),
                List.of(efectivo("89.90", null))));
        UUID codigo = comprobanteRepository.findById(boleta.id()).orElseThrow().getCodigoConsulta();

        mvc.perform(get("/api/publico/comprobantes/{c}/pdf", codigo))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mvc.perform(get("/api/publico/comprobantes/{c}/xml", codigo)).andExpect(status().isOk());
        mvc.perform(get("/api/publico/comprobantes/{c}/pdf", UUID.randomUUID())).andExpect(status().isNotFound());
        // El resto de la API sigue cerrada sin sesión.
        mvc.perform(get("/api/ventas/{id}/entrega", boleta.id())).andExpect(status().isUnauthorized());
    }

    // ---------- Ayudas ----------

    private VentaResponse aceptada(VentaResponse comprobante) {
        envioSunatService.enviarAhora(comprobante.id(), false);
        return ventaService.obtenerDetalle(comprobante.id());
    }

    private static MimeMessage leer(String nombre) throws Exception {
        try (InputStream entrada = Files.newInputStream(CARPETA.resolve(nombre + ".eml"))) {
            return new MimeMessage(Session.getInstance(new Properties()), entrada);
        }
    }

    private static List<String> adjuntos(Part parte) throws Exception {
        List<String> nombres = new ArrayList<>();
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                nombres.addAll(adjuntos(multipart.getBodyPart(i)));
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
            nombres.add(parte.getFileName());
        }
        return nombres;
    }

    /** Primera parte de texto simple del correo. */
    private static String texto(Part parte) throws Exception {
        if (parte.isMimeType("text/plain") && !Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
            return (String) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart hijo = multipart.getBodyPart(i);
                String texto = texto(hijo);
                if (texto != null) {
                    return texto;
                }
            }
        }
        return null;
    }
}
