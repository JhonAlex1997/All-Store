package com.allstore.api.facturacion.service;

import com.allstore.api.facturacion.config.SunatProperties;
import lombok.extern.slf4j.Slf4j;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cliente de la API REST de guías de remisión de SUNAT:
 * <ol>
 *   <li>Token OAuth2 (grant password) con las credenciales de la API y el usuario SOL; dura
 *       alrededor de una hora y se reutiliza hasta poco antes de vencer.</li>
 *   <li>POST del zip en base64 con su hash SHA-256: SUNAT responde con un número de ticket.</li>
 *   <li>GET del ticket: 98 = en proceso; 0 = aceptado (con CDR); 99 = rechazado, con CDR o con
 *       el error que impidió procesarlo.</li>
 * </ol>
 */
@Slf4j
@Component
public class ClienteRestGre implements ServicioGuiaSunat {

    private static final String ALCANCE = "https://api-cpe.sunat.gob.pe";
    /** Margen para renovar el token antes de que venza. */
    private static final Duration MARGEN_TOKEN = Duration.ofMinutes(2);

    private final SunatProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private record Token(String valor, Instant vence) {
    }

    private volatile Token token;

    public ClienteRestGre(SunatProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public boolean configurado() {
        return properties.greConfigurada();
    }

    @Override
    public ServicioEnvioSunat.Ticket enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException {
        byte[] zip = CdrSunat.comprimir(nombreArchivo + ".xml", xmlFirmado);
        Map<String, Object> archivo = new LinkedHashMap<>();
        archivo.put("nomArchivo", nombreArchivo + ".zip");
        archivo.put("arcGreZip", Base64.getEncoder().encodeToString(zip));
        archivo.put("hashZip", sha256(zip));
        String cuerpo = json.writeValueAsString(Map.of("archivo", archivo));
        HttpResponse<String> respuesta = conToken(nombreArchivo, t -> HttpRequest.newBuilder(
                        URI.create(properties.urlGreApi() + "/contribuyente/gem/comprobantes/" + nombreArchivo))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + t)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build());
        JsonNode datos = leerJson(respuesta);
        String ticket = texto(datos, "numTicket");
        if (respuesta.statusCode() == 200 && ticket != null) {
            return new ServicioEnvioSunat.Ticket(ticket, null);
        }
        return new ServicioEnvioSunat.Ticket(null, error(respuesta.statusCode(), datos));
    }

    @Override
    public ServicioEnvioSunat.EstadoTicket consultar(String nombreArchivo, String ticket) throws IOException {
        HttpResponse<String> respuesta = conToken(nombreArchivo, t -> HttpRequest.newBuilder(
                        URI.create(properties.urlGreApi() + "/contribuyente/gem/comprobantes/envios/"
                                + URLEncoder.encode(ticket, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + t)
                .GET()
                .build());
        return interpretarEstado(respuesta.statusCode(), leerJson(respuesta));
    }

    /** codRespuesta: 98 en proceso; 0 aceptado; 99 rechazado (con CDR, o con el error sin CDR). */
    ServicioEnvioSunat.EstadoTicket interpretarEstado(int estadoHttp, JsonNode datos) throws IOException {
        String codigo = texto(datos, "codRespuesta");
        if ("98".equals(codigo)) {
            return ServicioEnvioSunat.EstadoTicket.procesando();
        }
        String cdr = texto(datos, "arcCdr");
        if (cdr != null && !cdr.isBlank()) {
            return ServicioEnvioSunat.EstadoTicket.terminado(CdrSunat.leer(Base64.getDecoder().decode(cdr.trim())));
        }
        JsonNode error = datos.get("error");
        if (error != null && !error.isNull()) {
            return ServicioEnvioSunat.EstadoTicket.terminado(new ServicioEnvioSunat.Respuesta(false,
                    texto(error, "numError"), texto(error, "desError"), List.of(), null));
        }
        return ServicioEnvioSunat.EstadoTicket.terminado(error(estadoHttp, datos));
    }

    // ---------- Token ----------

    private interface Peticion {
        HttpRequest crear(String token);
    }

    /** Envía con el token vigente; si SUNAT lo rechaza (401), lo renueva y reintenta una vez. */
    private HttpResponse<String> conToken(String nombreArchivo, Peticion peticion) throws IOException {
        HttpResponse<String> respuesta = llamar(peticion.crear(token(nombreArchivo, false)));
        if (respuesta.statusCode() == 401) {
            respuesta = llamar(peticion.crear(token(nombreArchivo, true)));
        }
        if (respuesta.statusCode() >= 500) {
            throw new IOException("SUNAT respondió HTTP " + respuesta.statusCode());
        }
        return respuesta;
    }

    /** El usuario es el RUC (inicio del nombre del archivo) seguido del usuario SOL. */
    private synchronized String token(String nombreArchivo, boolean renovar) throws IOException {
        Token actual = token;
        if (!renovar && actual != null && Instant.now().isBefore(actual.vence())) {
            return actual.valor();
        }
        SunatProperties.Gre gre = properties.gre();
        Map<String, String> formulario = new LinkedHashMap<>();
        formulario.put("grant_type", "password");
        formulario.put("scope", ALCANCE);
        formulario.put("client_id", gre.clientId());
        formulario.put("client_secret", gre.clientSecret());
        formulario.put("username", nombreArchivo.substring(0, 11) + properties.usuarioSol());
        formulario.put("password", properties.claveSol() == null ? "" : properties.claveSol());
        String cuerpo = formulario.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpResponse<String> respuesta = llamar(HttpRequest.newBuilder(URI.create(properties.urlGreSeguridad()
                        + "/clientessol/" + URLEncoder.encode(gre.clientId(), StandardCharsets.UTF_8) + "/oauth2/token/"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build());
        JsonNode datos = leerJson(respuesta);
        String valor = texto(datos, "access_token");
        if (respuesta.statusCode() != 200 || valor == null) {
            // Sin el token no hay envío: se reintentará (y el panel muestra el motivo).
            throw new IOException("SUNAT no entregó el token de la API de guías (HTTP " + respuesta.statusCode() + "): "
                    + recortar(respuesta.body()));
        }
        long segundos = datos.has("expires_in") ? datos.get("expires_in").asLong(3600) : 3600;
        token = new Token(valor, Instant.now().plusSeconds(segundos).minus(MARGEN_TOKEN));
        return valor;
    }

    // ---------- Utilidades ----------

    private HttpResponse<String> llamar(HttpRequest peticion) throws IOException {
        try {
            return http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío interrumpido", e);
        }
    }

    private JsonNode leerJson(HttpResponse<String> respuesta) throws IOException {
        String cuerpo = respuesta.body();
        if (cuerpo == null || cuerpo.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(cuerpo);
        } catch (RuntimeException e) {
            throw new IOException("Respuesta de SUNAT no reconocida (HTTP " + respuesta.statusCode() + "): " + recortar(cuerpo), e);
        }
    }

    /** Error sin constancia: el archivo no se registró. Formato: {cod, msg, errors: [{cod, msg}]}. */
    private static ServicioEnvioSunat.Respuesta error(int estadoHttp, JsonNode datos) {
        String codigo = texto(datos, "cod");
        StringBuilder mensaje = new StringBuilder(texto(datos, "msg") == null ? "HTTP " + estadoHttp : texto(datos, "msg"));
        JsonNode errores = datos.get("errors");
        if (errores != null && errores.isArray()) {
            for (JsonNode e : errores) {
                if (codigo == null) {
                    codigo = texto(e, "cod");
                }
                mensaje.append(" | ").append(texto(e, "cod")).append(" ").append(texto(e, "msg"));
            }
        }
        return new ServicioEnvioSunat.Respuesta(false, codigo == null ? String.valueOf(estadoHttp) : codigo,
                mensaje.toString(), List.of(), null);
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo == null ? null : nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    /** Hash del zip que pide SUNAT: SHA-256 en hexadecimal. */
    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String texto) {
        return texto == null ? "" : texto.substring(0, Math.min(300, texto.length()));
    }
}
