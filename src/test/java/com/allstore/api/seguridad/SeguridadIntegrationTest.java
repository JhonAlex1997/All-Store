package com.allstore.api.seguridad;

import com.allstore.api.cliente.repository.ClienteRepository;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.entity.Usuario;
import com.allstore.api.seguridad.repository.UsuarioRepository;
import com.allstore.api.seguridad.service.UsuarioService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la seguridad de punta a punta (filtros, tokens, permisos) contra allstore_test. Cada
 * prueba es transaccional y se revierte al terminar.
 */
@SpringBootTest
@Transactional
class SeguridadIntegrationTest {

    private static final String PASSWORD = "Prueba2026x";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarioService.registrar("t_admin", "Admin Prueba", Rol.ADMIN, PASSWORD, false);
        usuarioService.registrar("t_cajero", "Cajero Prueba", Rol.CAJERO, PASSWORD, false);
        usuarioService.registrar("t_almacen", "Almacenero Prueba", Rol.ALMACENERO, PASSWORD, false);
    }

    @Test
    void sinTokenNoSePuedeUsarLaApi() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Debes iniciar sesión"));
    }

    @Test
    void tokenFalsificadoEsRechazado() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.firma-falsa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void loginDevuelveTokenYPerfil() throws Exception {
        String token = login("T_ADMIN", PASSWORD); // el usuario no distingue mayúsculas
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("t_admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void credencialesIncorrectasDanElMismoMensajeExistaONoElUsuario() throws Exception {
        intentarLogin("t_cajero", "Incorrecta123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
        intentarLogin("no_existe", "Incorrecta123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void cincoIntentosFallidosBloqueanElUsuario() throws Exception {
        for (int i = 0; i < Usuario.MAX_INTENTOS_FALLIDOS; i++) {
            intentarLogin("t_cajero", "Incorrecta123");
        }
        // Bloqueado incluso con la contraseña correcta.
        intentarLogin("t_cajero", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bloqueado")));
    }

    @Test
    void cajeroNoModificaCatalogoPeroSiClientes() throws Exception {
        String token = login("t_cajero", PASSWORD);
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/categorias").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Polo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta acción"));
        // 400 = pasó la seguridad y llegó a la validación del cuerpo
        mockMvc.perform(post("/api/clientes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloAdministradorGestionaUsuariosYSeries() throws Exception {
        String almacenero = login("t_almacen", PASSWORD);
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + almacenero))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/series").header("Authorization", "Bearer " + almacenero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        String admin = login("t_admin", PASSWORD);
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void usuarioNuevoDebeCambiarSuPasswordAntesDeTrabajar() throws Exception {
        usuarioService.registrar("t_nuevo", "Usuario Nuevo", Rol.CAJERO, PASSWORD, true);
        String temporal = login("t_nuevo", PASSWORD);

        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + temporal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Debes cambiar tu contraseña antes de continuar"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + temporal))
                .andExpect(status().isOk());

        Thread.sleep(1100); // el token anterior queda emitido antes del cambio (precisión de segundos)
        String nuevo = JsonPath.read(mockMvc.perform(post("/api/auth/cambiar-password")
                        .header("Authorization", "Bearer " + temporal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"NuevaClave2026\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + nuevo))
                .andExpect(status().isOk());
        // El token de antes del cambio ya no sirve (sesiones cerradas).
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + temporal))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesactivadoPierdeSuSesionDeInmediato() throws Exception {
        String token = login("t_cajero", PASSWORD);
        Usuario cajero = usuarioRepository.findByUsernameIgnoreCase("t_cajero").orElseThrow();
        cajero.setActivo(false);

        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tu sesión ya no es válida, vuelve a iniciar sesión"));
    }

    @Test
    void cadaRegistroGuardaQuienLoCreo() throws Exception {
        String token = login("t_cajero", PASSWORD);
        mockMvc.perform(post("/api/clientes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"87654321\",\"nombre\":\"Cliente Auditado\"}"))
                .andExpect(status().isCreated());

        var cliente = clienteRepository.findByTipoDocumentoAndNumeroDocumento(
                com.allstore.api.cliente.entity.TipoDocumentoIdentidad.DNI, "87654321").orElseThrow();
        assertThat(cliente.getCreatedBy()).isEqualTo("t_cajero");
    }

    private String login(String username, String password) throws Exception {
        String body = intentarLogin(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions intentarLogin(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }
}
