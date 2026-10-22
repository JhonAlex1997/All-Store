package com.allstore.api.facturacion;

import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.service.ClienteRestGre;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cliente de la API REST de guías contra un servidor local que imita a SUNAT (SUNAT no tiene
 * entorno de pruebas para guías): token OAuth, forma del envío, consulta del ticket y renovación
 * del token cuando vence.
 */
class ClienteRestGreTest {

    private static final String ARCHIVO = "20000000001-09-T001-1";
    private final JsonMapper json = JsonMapper.builder().build();

    private HttpServer servidor;
    private ClienteRestGre cliente;
    private final AtomicInteger tokensEntregados = new AtomicInteger();
    private final AtomicInteger consultas = new AtomicInteger();
    private final List<Map<String, String>> formulariosToken = new ArrayList<>();
    private JsonNode ultimoEnvio;
    private String ultimaAutorizacion;
    /** Cuántas veces más el envío responderá 401 (token vencido). */
    private final AtomicInteger rechazosPorToken = new AtomicInteger();

    @BeforeEach
    void levantarSunatSimulada() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/seguridad/clientessol/mi-cliente/oauth2/token/", this::token);
        servidor.createContext("/api/contribuyente/gem/comprobantes/envios/", this::estado);
        servidor.createContext("/api/contribuyente/gem/comprobantes/" + ARCHIVO, this::envio);
        servidor.start();
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        cliente = new ClienteRestGre(new SunatProperties(true, EntornoSunat.PRODUCCION, null, "MODDATOS", "moddatos", null,
                null, 30000, 8, new SunatProperties.Gre("mi-cliente", "secreto", base + "/seguridad", base + "/api")), json);
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    @Test
    void enviaElZipConSuHashYConsultaElTicketHastaTenerLaConstancia() throws Exception {
        byte[] xml = "<DespatchAdvice/>".getBytes(StandardCharsets.UTF_8);

        ServicioEnvioSunat.Ticket ticket = cliente.enviar(ARCHIVO, xml);

        assertThat(ticket.numero()).isEqualTo("4f2c1a7e-0001");
        Map<String, String> formulario = formulariosToken.getFirst();
        assertThat(formulario).containsEntry("grant_type", "password").containsEntry("scope", "https://api-cpe.sunat.gob.pe")
                .containsEntry("client_id", "mi-cliente").containsEntry("client_secret", "secreto")
                .containsEntry("username", "20000000001MODDATOS").containsEntry("password", "moddatos");
        assertThat(ultimaAutorizacion).isEqualTo("Bearer token-1");
        JsonNode archivo = ultimoEnvio.get("archivo");
        assertThat(archivo.get("nomArchivo").asString()).isEqualTo(ARCHIVO + ".zip");
        byte[] zip = Base64.getDecoder().decode(archivo.get("arcGreZip").asString());
        assertThat(archivo.get("hashZip").asString())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)));
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertThat(entrada.getNextEntry().getName()).isEqualTo(ARCHIVO + ".xml");
            assertThat(entrada.readAllBytes()).isEqualTo(xml);
        }

        assertThat(cliente.consultar(ARCHIVO, ticket.numero()).enProceso()).isTrue();
        ServicioEnvioSunat.EstadoTicket terminado = cliente.consultar(ARCHIVO, ticket.numero());
        assertThat(terminado.enProceso()).isFalse();
        assertThat(terminado.respuesta().cdrRecibido()).isTrue();
        assertThat(terminado.respuesta().codigoNumerico()).isZero();
        assertThat(tokensEntregados).as("el token se reutiliza mientras no venza").hasValue(1);
    }

    @Test
    void siElTokenVencioLoRenuevaYReintentaUnaVez() throws Exception {
        rechazosPorToken.set(1);

        ServicioEnvioSunat.Ticket ticket = cliente.enviar(ARCHIVO, "<DespatchAdvice/>".getBytes(StandardCharsets.UTF_8));

        assertThat(ticket.numero()).isEqualTo("4f2c1a7e-0001");
        assertThat(tokensEntregados).hasValue(2);
        assertThat(ultimaAutorizacion).isEqualTo("Bearer token-2");
    }

    @Test
    void ticketRechazadoSinConstanciaTraeElError() throws Exception {
        ServicioEnvioSunat.EstadoTicket estado = cliente.consultar(ARCHIVO, "ticket-con-error");

        assertThat(estado.enProceso()).isFalse();
        assertThat(estado.respuesta().cdrRecibido()).isFalse();
        assertThat(estado.respuesta().codigo()).isEqualTo("2801");
        assertThat(estado.respuesta().descripcion()).contains("ubigeo");
    }

    // ---------- SUNAT simulada ----------

    private void token(HttpExchange intercambio) throws IOException {
        Map<String, String> formulario = new HashMap<>();
        for (String par : new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
            String[] kv = par.split("=", 2);
            formulario.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        formulariosToken.add(formulario);
        responder(intercambio, 200, "{\"access_token\":\"token-" + tokensEntregados.incrementAndGet()
                + "\",\"token_type\":\"JWT\",\"expires_in\":3600}");
    }

    private void envio(HttpExchange intercambio) throws IOException {
        ultimaAutorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        if (rechazosPorToken.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            responder(intercambio, 401, "");
            return;
        }
        ultimoEnvio = json.readTree(intercambio.getRequestBody().readAllBytes());
        responder(intercambio, 200, "{\"numTicket\":\"4f2c1a7e-0001\",\"fecRecepcion\":\"2026-10-22T10:00:00\"}");
    }

    private void estado(HttpExchange intercambio) throws IOException {
        if (intercambio.getRequestURI().getPath().endsWith("ticket-con-error")) {
            responder(intercambio, 200, "{\"codRespuesta\":\"99\",\"indCdrGenerado\":\"0\","
                    + "\"error\":{\"numError\":\"2801\",\"desError\":\"El ubigeo de llegada no existe\"}}");
            return;
        }
        if (consultas.incrementAndGet() == 1) {
            responder(intercambio, 200, "{\"codRespuesta\":\"98\"}");
            return;
        }
        responder(intercambio, 200, "{\"codRespuesta\":\"0\",\"indCdrGenerado\":\"1\",\"arcCdr\":\""
                + Base64.getEncoder().encodeToString(cdrAceptado()) + "\"}");
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            intercambio.getResponseBody().write(bytes);
        }
        intercambio.close();
    }

    private static byte[] cdrAceptado() throws IOException {
        String cdr = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2" \
                xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" \
                xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">\
                <cac:DocumentResponse><cac:Response><cbc:ReferenceID>T001-1</cbc:ReferenceID>\
                <cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Guia numero T001-1, ha sido aceptada</cbc:Description>\
                </cac:Response><cac:DocumentReference><cbc:DocumentDescription>https://e-factura.sunat.gob.pe/consulta?hashqr=abc\
                </cbc:DocumentDescription></cac:DocumentReference></cac:DocumentResponse></ar:ApplicationResponse>""";
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream salida = new ZipOutputStream(zip)) {
            salida.putNextEntry(new ZipEntry("R-" + ARCHIVO + ".xml"));
            salida.write(cdr.getBytes(StandardCharsets.UTF_8));
            salida.closeEntry();
        }
        return zip.toByteArray();
    }
}
