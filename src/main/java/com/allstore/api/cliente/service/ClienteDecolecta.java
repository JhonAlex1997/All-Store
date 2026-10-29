package com.allstore.api.cliente.service;

import com.allstore.api.cliente.config.ConsultaDocumentosProperties;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Proveedor Decolecta (https://decolecta.gitbook.io/docs):
 * <ul>
 *   <li>RUC: GET /v1/sunat/ruc?numero= → razon_social, direccion, ubigeo, estado, condicion.</li>
 *   <li>DNI: GET /v1/reniec/dni?numero= → full_name (apellidos y nombres).</li>
 * </ul>
 * El token va en Authorization: Bearer. Un número que no existe responde 404 o 422.
 */
@Component
public class ClienteDecolecta implements ProveedorDocumentos {

    private final ConsultaDocumentosProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ClienteDecolecta(ConsultaDocumentosProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public Optional<DatosDocumento> consultar(TipoDocumentoIdentidad tipo, String numero) throws IOException {
        String ruta = tipo == TipoDocumentoIdentidad.RUC ? "/v1/sunat/ruc" : "/v1/reniec/dni";
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(properties.url() + ruta + "?numero="
                        + URLEncoder.encode(numero, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.token())
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta interrumpida", e);
        }
        int estado = respuesta.statusCode();
        if (estado == 404 || estado == 422 || estado == 400) {
            return Optional.empty();
        }
        if (estado == 401 || estado == 403) {
            throw new IOException("El proveedor rechazó el token (app.consulta-documentos.token)");
        }
        if (estado != 200) {
            throw new IOException("El proveedor respondió HTTP " + estado);
        }
        return Optional.of(leer(tipo, json.readTree(respuesta.body())));
    }

    static DatosDocumento leer(TipoDocumentoIdentidad tipo, JsonNode datos) {
        if (tipo == TipoDocumentoIdentidad.RUC) {
            return new DatosDocumento(texto(datos, "razon_social"), texto(datos, "direccion"), texto(datos, "ubigeo"),
                    texto(datos, "estado"), texto(datos, "condicion"));
        }
        String nombre = texto(datos, "full_name");
        if (nombre == null) {
            nombre = String.join(" ", java.util.stream.Stream.of(texto(datos, "first_last_name"),
                    texto(datos, "second_last_name"), texto(datos, "first_name")).filter(java.util.Objects::nonNull).toList());
        }
        return new DatosDocumento(nombre, null, null, null, null);
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String texto = valor.asString().trim().replaceAll("\\s+", " ");
        return texto.isEmpty() || "-".equals(texto) ? null : texto;
    }
}
