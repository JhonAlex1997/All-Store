package com.allstore.api.entrega.service;

import com.allstore.api.entrega.config.WhatsappProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente de WhatsApp Cloud API: POST {urlApi}/{numeroId}/messages con un mensaje de plantilla
 * cuyo encabezado es el PDF (por enlace) y cuyo cuerpo lleva los textos. Meta descarga el PDF desde
 * el enlace público del comprobante, así que este debe ser accesible desde internet (https).
 */
@Component
public class ServicioWhatsapp {

    private final WhatsappProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ServicioWhatsapp(WhatsappProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public boolean configurado() {
        return properties.configurado();
    }

    /**
     * @param telefono     con código de país, solo dígitos
     * @param enlacePdf    enlace público del PDF
     * @param nombrePdf    nombre con que lo verá el cliente (ej. B001-00000128.pdf)
     * @param textos       las variables del cuerpo de la plantilla, en orden
     * @return el id del mensaje en Meta (wamid...)
     * @throws IOException si Meta no lo acepta o no hay comunicación (con el motivo)
     */
    public String enviar(String telefono, String enlacePdf, String nombrePdf, List<String> textos) throws IOException {
        Map<String, Object> cuerpo = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", telefono,
                "type", "template",
                "template", Map.of(
                        "name", properties.plantilla(),
                        "language", Map.of("code", properties.idioma()),
                        "components", List.of(
                                Map.of("type", "header", "parameters", List.of(Map.of(
                                        "type", "document",
                                        "document", Map.of("link", enlacePdf, "filename", nombrePdf)))),
                                Map.of("type", "body", "parameters",
                                        textos.stream().map(t -> Map.of("type", "text", "text", t)).toList()))));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(properties.urlApi() + "/" + properties.numeroId() + "/messages"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + properties.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío interrumpido", e);
        }
        JsonNode datos = respuesta.body().isBlank() ? null : json.readTree(respuesta.body());
        if (respuesta.statusCode() / 100 != 2) {
            JsonNode error = datos == null ? null : datos.get("error");
            String motivo = error != null && error.get("message") != null ? error.get("message").asString() : "HTTP " + respuesta.statusCode();
            throw new IOException("WhatsApp no aceptó el mensaje: " + motivo);
        }
        JsonNode mensajes = datos == null ? null : datos.get("messages");
        if (mensajes == null || mensajes.isEmpty() || mensajes.get(0).get("id") == null) {
            throw new IOException("WhatsApp respondió sin el id del mensaje");
        }
        return mensajes.get(0).get("id").asString();
    }
}
