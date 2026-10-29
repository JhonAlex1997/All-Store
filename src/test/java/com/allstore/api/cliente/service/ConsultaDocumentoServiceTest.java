package com.allstore.api.cliente.service;

import com.allstore.api.cliente.config.ConsultaDocumentosProperties;
import com.allstore.api.cliente.config.ConsultaDocumentosProperties.Proveedor;
import com.allstore.api.cliente.dto.ConsultaDocumentoResponse;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.repository.ClienteRepository;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Consulta de RUC y DNI: validación previa, clientes registrados, proveedor (Decolecta simulado) y caché. */
class ConsultaDocumentoServiceTest {

    private static final String RUC = """
            {"razon_social": "REXTIE S.A.C.", "numero_documento": "20601030013", "estado": "ACTIVO",
             "condicion": "HABIDO", "direccion": "AV. JOSE GALVEZ BARRENECHEA NRO 566 INT. 101 URB. CORPAC ",
             "ubigeo": "150131", "distrito": "SAN ISIDRO"}""";
    private static final String DNI = """
            {"first_name": "ROXANA KARINA", "first_last_name": "DELGADO", "second_last_name": "HUAMANI",
             "full_name": "DELGADO HUAMANI ROXANA KARINA", "document_number": "46027897"}""";

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final List<String> pedidos = new ArrayList<>();
    private HttpServer servidor;

    @AfterEach
    void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void consultaRucEnDecolectaConElTokenYLeeSusDatos() throws Exception {
        ConsultaDocumentoService servicio = conDecolecta();

        ConsultaDocumentoResponse r = servicio.consultar(TipoDocumentoIdentidad.RUC, "20601030013");

        assertThat(r.nombre()).isEqualTo("REXTIE S.A.C.");
        assertThat(r.direccion()).isEqualTo("AV. JOSE GALVEZ BARRENECHEA NRO 566 INT. 101 URB. CORPAC");
        assertThat(r.ubigeo()).isEqualTo("150131");
        assertThat(r.fuente()).isEqualTo("SUNAT");
        assertThat(r.advertencia()).isNull();
        assertThat(pedidos).containsExactly("/v1/sunat/ruc?numero=20601030013 Bearer token-prueba");
    }

    @Test
    void consultaDniEnReniecYRecuerdaLaRespuesta() throws Exception {
        ConsultaDocumentoService servicio = conDecolecta();

        assertThat(servicio.consultar(TipoDocumentoIdentidad.DNI, "46027897").nombre()).isEqualTo("DELGADO HUAMANI ROXANA KARINA");
        servicio.consultar(TipoDocumentoIdentidad.DNI, "46027897");

        // La segunda vez no se volvió a pagar.
        assertThat(pedidos).hasSize(1);
    }

    @Test
    void unRucMalEscritoNoSeConsultaYUnoRegistradoSaleDeLaBase() throws Exception {
        ConsultaDocumentoService servicio = conDecolecta();
        assertThatThrownBy(() -> servicio.consultar(TipoDocumentoIdentidad.RUC, "20601030014"))
                .isInstanceOf(BusinessException.class);

        Cliente registrado = new Cliente();
        registrado.setNombre("EMPRESA REGISTRADA SAC");
        when(clientes.findByTipoDocumentoAndNumeroDocumento(TipoDocumentoIdentidad.RUC, "20601030013"))
                .thenReturn(Optional.of(registrado));
        assertThat(servicio.consultar(TipoDocumentoIdentidad.RUC, "20601030013").fuente()).isEqualTo("REGISTRADO");
        assertThat(pedidos).isEmpty();
    }

    @Test
    void avisaSiElRucNoEstaActivoYHabido() throws Exception {
        ProveedorDocumentos proveedor = mock(ProveedorDocumentos.class);
        when(proveedor.consultar(any(), anyString())).thenReturn(Optional.of(
                new ProveedorDocumentos.DatosDocumento("FANTASMA SAC", null, null, "BAJA DE OFICIO", "NO HABIDO")));
        ConsultaDocumentoService servicio = new ConsultaDocumentoService(clientes, proveedor, propiedades("http://no-usado"));

        assertThat(servicio.consultar(TipoDocumentoIdentidad.RUC, "20601030013").advertencia())
                .contains("BAJA DE OFICIO", "NO HABIDO", "no conviene emitirle factura");
    }

    @Test
    void sinProveedorONumeroInexistente() throws Exception {
        ConsultaDocumentoService desactivada = new ConsultaDocumentoService(clientes, mock(ProveedorDocumentos.class),
                new ConsultaDocumentosProperties(Proveedor.DESACTIVADO, null, "x", 24));
        assertThatThrownBy(() -> desactivada.consultar(TipoDocumentoIdentidad.DNI, "46027897"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("a mano");

        ConsultaDocumentoService servicio = conDecolecta();
        assertThatThrownBy(() -> servicio.consultar(TipoDocumentoIdentidad.DNI, "11111111"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Decolecta simulado ----------

    private ConsultaDocumentoService conDecolecta() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String uri = intercambio.getRequestURI().toString();
            pedidos.add(uri + " " + intercambio.getRequestHeaders().getFirst("Authorization"));
            String cuerpo = uri.startsWith("/v1/sunat/ruc") ? RUC : uri.contains("46027897") ? DNI : null;
            byte[] bytes = (cuerpo == null ? "{\"message\": \"no encontrado\"}" : cuerpo).getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(cuerpo == null ? 404 : 200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        ConsultaDocumentosProperties propiedades = propiedades("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new ConsultaDocumentoService(clientes, new ClienteDecolecta(propiedades, JsonMapper.builder().build()), propiedades);
    }

    private static ConsultaDocumentosProperties propiedades(String url) {
        return new ConsultaDocumentosProperties(Proveedor.DECOLECTA, "token-prueba", url, 24);
    }
}
