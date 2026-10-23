package com.allstore.api.facturacion;

import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.service.ClienteSoapSunat;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura de las respuestas de SUNAT: constancia (CDR) y excepciones SOAP, sin red. */
class RespuestaSunatTest {

    private final ClienteSoapSunat cliente = new ClienteSoapSunat(
            new SunatProperties(true, EntornoSunat.BETA, null, null, "MODDATOS", "moddatos", null, null, 30000, 8, null));

    @Test
    void cdrAceptadoConObservaciones() throws Exception {
        ServicioEnvioSunat.Respuesta r = interpretar(respuestaConCdr("0", "La Boleta numero B001-5, ha sido aceptada",
                "<cbc:Note>4288 - El valor de venta por ítem difiere</cbc:Note>"));

        assertThat(r.cdrRecibido()).isTrue();
        assertThat(r.codigoNumerico()).isZero();
        assertThat(r.descripcion()).contains("aceptada");
        assertThat(r.notas()).containsExactly("4288 - El valor de venta por ítem difiere");
        assertThat(r.cdr()).isNotEmpty();
    }

    @Test
    void cdrRechazado() throws Exception {
        ServicioEnvioSunat.Respuesta r = interpretar(respuestaConCdr("2335", "El documento electrónico ha sido alterado", ""));

        assertThat(r.cdrRecibido()).isTrue();
        assertThat(r.codigoNumerico()).isEqualTo(2335);
        assertThat(r.notas()).isEmpty();
    }

    @Test
    void excepcionSoapConCodigo() throws Exception {
        String fault = """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"><soap-env:Body>\
                <soap-env:Fault><faultcode>soap-env:Client.0111</faultcode>\
                <faultstring>No tiene el perfil para enviar comprobantes electronicos</faultstring>\
                </soap-env:Fault></soap-env:Body></soap-env:Envelope>""";

        ServicioEnvioSunat.Respuesta r = interpretar(fault);

        assertThat(r.cdrRecibido()).isFalse();
        assertThat(r.codigo()).isEqualTo("0111");
        assertThat(r.descripcion()).contains("perfil");
    }

    @Test
    void ticketEnProceso() throws Exception {
        ServicioEnvioSunat.EstadoTicket estado = interpretarEstado(respuestaEstado("98", null));

        assertThat(estado.enProceso()).isTrue();
        assertThat(estado.respuesta()).isNull();
    }

    @Test
    void ticketTerminadoTraeLaConstancia() throws Exception {
        String cdr = Base64.getEncoder().encodeToString(zipCdr("0", "La Comunicacion de baja RA-20261022-1, ha sido aceptada", ""));

        ServicioEnvioSunat.EstadoTicket estado = interpretarEstado(respuestaEstado("0", cdr));

        assertThat(estado.enProceso()).isFalse();
        assertThat(estado.respuesta().cdrRecibido()).isTrue();
        assertThat(estado.respuesta().codigoNumerico()).isZero();
        assertThat(estado.respuesta().descripcion()).contains("RA-20261022-1");
    }

    @Test
    void consultaDeConstanciaQueLaEntrega() throws Exception {
        String cdr = Base64.getEncoder().encodeToString(zipCdr("0", "La Factura numero F001-7, ha sido aceptada", ""));

        ServicioEnvioSunat.Respuesta r = interpretarConsulta(respuestaConsulta("0004", "La constancia existe", cdr));

        assertThat(r.cdrRecibido()).isTrue();
        assertThat(r.codigoNumerico()).isZero();
        assertThat(r.descripcion()).contains("F001-7");
    }

    @Test
    void consultaDeConstanciaSinElla() throws Exception {
        ServicioEnvioSunat.Respuesta r = interpretarConsulta(
                respuestaConsulta("0011", "El comprobante de pago electronico no existe", null));

        assertThat(r.cdrRecibido()).isFalse();
        assertThat(r.codigo()).isEqualTo("0011");
        assertThat(r.descripcion()).contains("no existe");
    }

    // ---------- Ayudas ----------

    private ServicioEnvioSunat.Respuesta interpretarConsulta(String cuerpo) throws Exception {
        Method m = ClienteSoapSunat.class.getDeclaredMethod("interpretarConsulta", String.class);
        m.setAccessible(true);
        return (ServicioEnvioSunat.Respuesta) m.invoke(cliente, cuerpo);
    }

    /** Respuesta SOAP de getStatusCdr (billConsultService), con el esquema que publica SUNAT. */
    private static String respuestaConsulta(String codigo, String mensaje, String contenido) {
        return """
                <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/"><S:Body>                <ns2:getStatusCdrResponse xmlns:ns2="http://service.sunat.gob.pe"><statusCdr>%s                <statusCode>%s</statusCode><statusMessage>%s</statusMessage></statusCdr>                </ns2:getStatusCdrResponse></S:Body></S:Envelope>"""
                .formatted(contenido == null ? "" : "<content>" + contenido + "</content>", codigo, mensaje);
    }

    private ServicioEnvioSunat.Respuesta interpretar(String cuerpo) throws Exception {
        Method m = ClienteSoapSunat.class.getDeclaredMethod("interpretar", String.class);
        m.setAccessible(true);
        return (ServicioEnvioSunat.Respuesta) m.invoke(cliente, cuerpo);
    }

    private ServicioEnvioSunat.EstadoTicket interpretarEstado(String cuerpo) throws Exception {
        Method m = ClienteSoapSunat.class.getDeclaredMethod("interpretarEstado", String.class);
        m.setAccessible(true);
        return (ServicioEnvioSunat.EstadoTicket) m.invoke(cliente, cuerpo);
    }

    /** Respuesta SOAP de getStatus, como la envía SUNAT. */
    private static String respuestaEstado(String codigo, String contenido) {
        return """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"><soap-env:Body>\
                <br:getStatusResponse xmlns:br="http://service.sunat.gob.pe"><status>%s<statusCode>%s</statusCode>\
                </status></br:getStatusResponse></soap-env:Body></soap-env:Envelope>"""
                .formatted(contenido == null ? "" : "<content>" + contenido + "</content>", codigo);
    }

    /** Respuesta SOAP de sendBill con un CDR (ApplicationResponse) comprimido como lo envía SUNAT. */
    private static String respuestaConCdr(String codigo, String descripcion, String notas) throws Exception {
        return """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"><soap-env:Body>\
                <br:sendBillResponse xmlns:br="http://service.sunat.gob.pe">\
                <applicationResponse>%s</applicationResponse></br:sendBillResponse>\
                </soap-env:Body></soap-env:Envelope>""".formatted(
                Base64.getEncoder().encodeToString(zipCdr(codigo, descripcion, notas)));
    }

    /** CDR (ApplicationResponse) comprimido en zip, como lo entrega SUNAT. */
    private static byte[] zipCdr(String codigo, String descripcion, String notas) throws Exception {
        String cdr = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2" \
                xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" \
                xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">%s\
                <cac:DocumentResponse><cac:Response><cbc:ReferenceID>B001-5</cbc:ReferenceID>\
                <cbc:ResponseCode>%s</cbc:ResponseCode><cbc:Description>%s</cbc:Description></cac:Response>\
                </cac:DocumentResponse></ar:ApplicationResponse>""".formatted(notas, codigo, descripcion);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(zip)) {
            out.putNextEntry(new ZipEntry("R-20000000001-03-B001-5.xml"));
            out.write(cdr.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip.toByteArray();
    }
}
