package com.allstore.api.entrega;

import com.allstore.api.entrega.config.WhatsappProperties;
import com.allstore.api.entrega.service.ServicioWhatsapp;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El mensaje que se envía a WhatsApp Cloud API (Meta simulado en local). */
class ServicioWhatsappTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> recibidos = new ArrayList<>();
    private HttpServer meta;
    private int codigoRespuesta = 200;
    private String respuesta = """
            {"messaging_product": "whatsapp", "contacts": [{"input": "51987654321", "wa_id": "51987654321"}],
             "messages": [{"id": "wamid.HBgLNTE5ODc2NTQzMjEVAgARGBI"}]}""";

    @AfterEach
    void apagar() {
        meta.stop(0);
    }

    @Test
    void enviaLaPlantillaConElPdfComoDocumentoYLosTextos() throws Exception {
        ServicioWhatsapp servicio = servicio();

        String id = servicio.enviar("51987654321", "https://tienda.pe/api/publico/comprobantes/abc/pdf", "B001-00000128.pdf",
                List.of("María Quispe", "boleta B001-00000128", "S/ 188.00", "AllStore Jeans"));

        assertThat(id).isEqualTo("wamid.HBgLNTE5ODc2NTQzMjEVAgARGBI");
        assertThat(recibidos.get(0)).isEqualTo("POST /v26.0/123456789/messages Bearer token-meta");
        JsonNode cuerpo = json.readTree(recibidos.get(1));
        assertThat(cuerpo.get("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(cuerpo.get("to").asString()).isEqualTo("51987654321");
        assertThat(cuerpo.get("type").asString()).isEqualTo("template");
        JsonNode plantilla = cuerpo.get("template");
        assertThat(plantilla.get("name").asString()).isEqualTo("comprobante_electronico");
        assertThat(plantilla.get("language").get("code").asString()).isEqualTo("es");
        JsonNode documento = plantilla.get("components").get(0).get("parameters").get(0).get("document");
        assertThat(plantilla.get("components").get(0).get("type").asString()).isEqualTo("header");
        assertThat(documento.get("link").asString()).endsWith("/abc/pdf");
        assertThat(documento.get("filename").asString()).isEqualTo("B001-00000128.pdf");
        JsonNode textos = plantilla.get("components").get(1).get("parameters");
        assertThat(textos).hasSize(4);
        assertThat(textos.get(0).get("text").asString()).isEqualTo("María Quispe");
    }

    @Test
    void siMetaLoRechazaDiceElMotivo() throws Exception {
        codigoRespuesta = 400;
        respuesta = "{\"error\": {\"message\": \"(#132001) Template name does not exist in the translation\", \"code\": 132001}}";

        assertThatThrownBy(() -> servicio().enviar("51987654321", "https://x/pdf", "x.pdf", List.of("a", "b", "c", "d")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Template name does not exist");
    }

    private ServicioWhatsapp servicio() throws Exception {
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", intercambio -> {
            recibidos.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI() + " "
                    + intercambio.getRequestHeaders().getFirst("Authorization"));
            recibidos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(codigoRespuesta, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        meta.start();
        return new ServicioWhatsapp(new WhatsappProperties("token-meta", "123456789", "comprobante_electronico", "es",
                "http://127.0.0.1:" + meta.getAddress().getPort() + "/v26.0"), json);
    }
}
