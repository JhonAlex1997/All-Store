package com.allstore.api.common.version;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.repository.ClienteRepository;
import com.allstore.api.cliente.service.ClienteService;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import com.allstore.api.seguridad.service.UsuarioService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dos personas editan el mismo registro: la que guarda con una versión vieja recibe 412 con quién lo
 * cambió, en lugar de pisar sus cambios sin aviso.
 */
@SpringBootTest
@Transactional
class EdicionConcurrenteIntegrationTest {

    private static final String PASSWORD = "Prueba2026x";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteService clienteService;
    @Autowired private ClienteRepository clienteRepository;

    /** DNI distinto en cada corrida: la base de pruebas puede conservar clientes de otras pruebas. */
    private static final String DNI = String.valueOf(70_000_000 + (System.nanoTime() % 9_999_999));

    private MockMvc mockMvc;
    private String ana;
    private String beto;
    private Long clienteId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarioService.registrar("v_ana", "Ana Versión", Rol.ADMIN, PASSWORD, false);
        usuarioService.registrar("v_beto", "Beto Versión", Rol.ADMIN, PASSWORD, false);
        ana = login("v_ana");
        beto = login("v_beto");
        clienteId = clienteService.crear(cliente("Cliente Original", "999111222")).id();
        clienteRepository.flush();
    }

    @Test
    void quienGuardaConLaVersionViejaRecibe412YNoPisaLosCambios() throws Exception {
        // Los dos abren el formulario y leen la misma versión.
        long version = version(ana);
        assertThat(version(beto)).isEqualTo(version);

        // Ana guarda primero: pasa y la versión sube.
        mockMvc.perform(put("/api/clientes/" + clienteId).header("Authorization", "Bearer " + ana)
                        .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json(cliente("Cliente de Ana", "999111222"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente de Ana"))
                .andExpect(jsonPath("$.version").value(version + 1));

        // Beto guarda con lo que leyó antes: se rechaza y se le dice quién lo cambió.
        mockMvc.perform(put("/api/clientes/" + clienteId).header("Authorization", "Bearer " + beto)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(version))
                        .contentType(MediaType.APPLICATION_JSON).content(json(cliente("Cliente de Beto", "988777666"))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.message").value(containsString("v_ana")))
                .andExpect(jsonPath("$.message").value(containsString("Recarga")));

        // Los datos de Ana siguen ahí.
        mockMvc.perform(get("/api/clientes/" + clienteId).header("Authorization", "Bearer " + beto))
                .andExpect(jsonPath("$.nombre").value("Cliente de Ana"))
                .andExpect(jsonPath("$.telefono").value("999111222"));

        // Beto recarga, toma la versión nueva y ahora sí guarda.
        long nueva = version(beto);
        mockMvc.perform(put("/api/clientes/" + clienteId).header("Authorization", "Bearer " + beto)
                        .header(HttpHeaders.IF_MATCH, "W/\"" + nueva + "\"")
                        .contentType(MediaType.APPLICATION_JSON).content(json(cliente("Cliente de Beto", "988777666"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(nueva + 1));
    }

    @Test
    void sinIfMatchSeGuardaComoAntes() throws Exception {
        long version = version(ana);
        mockMvc.perform(put("/api/clientes/" + clienteId).header("Authorization", "Bearer " + ana)
                        .contentType(MediaType.APPLICATION_JSON).content(json(cliente("Sin Versión", "999111222"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void unIfMatchQueNoEsNumeroSeRechaza() throws Exception {
        mockMvc.perform(put("/api/clientes/" + clienteId).header("Authorization", "Bearer " + ana)
                        .header(HttpHeaders.IF_MATCH, "abc")
                        .contentType(MediaType.APPLICATION_JSON).content(json(cliente("X", "999111222"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iniciarSesionNoCambiaLaVersionDelUsuario() throws Exception {
        long antes = usuarioRepository.findByUsernameIgnoreCase("v_beto").orElseThrow().getVersion();
        login("v_beto");
        usuarioRepository.flush();
        assertThat(usuarioRepository.findByUsernameIgnoreCase("v_beto").orElseThrow().getVersion())
                .as("entrar al sistema no es editar al usuario: no debe hacer fallar al administrador que lo edita")
                .isEqualTo(antes);
    }

    @Test
    void laCabeceraIfMatchAceptaLosFormatosHabituales() {
        assertThat(Versiones.deIfMatch(null)).isNull();
        assertThat(Versiones.deIfMatch("*")).isNull();
        assertThat(Versiones.deIfMatch(" ")).isNull();
        assertThat(Versiones.deIfMatch("7")).isEqualTo(7L);
        assertThat(Versiones.deIfMatch("\"7\"")).isEqualTo(7L);
        assertThat(Versiones.deIfMatch("W/\"7\"")).isEqualTo(7L);
        assertThatThrownBy(() -> Versiones.deIfMatch("siete")).hasMessageContaining("If-Match");
    }

    private long version(String token) throws Exception {
        String json = mockMvc.perform(get("/api/clientes/" + clienteId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    private String login(String usuario) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private static ClienteRequest cliente(String nombre, String telefono) {
        return new ClienteRequest(TipoDocumentoIdentidad.DNI, DNI, nombre, null, null, telefono, null, true);
    }

    private static String json(ClienteRequest r) {
        return """
                {"tipoDocumento":"%s","numeroDocumento":"%s","nombre":"%s","telefono":"%s","activo":true}"""
                .formatted(r.tipoDocumento(), r.numeroDocumento(), r.nombre(), r.telefono());
    }
}
