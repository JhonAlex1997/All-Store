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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void ventasSoloParaCajaYAnularSoloAdmin() throws Exception {
        String almacenero = login("t_almacen", PASSWORD);
        String cajero = login("t_cajero", PASSWORD);
        // 400 = pasó la seguridad y llegó a la validación del cuerpo
        mockMvc.perform(post("/api/ventas").header("Authorization", "Bearer " + almacenero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ventas").header("Authorization", "Bearer " + cajero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ventas/1/anular").header("Authorization", "Bearer " + cajero)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historialDeCajasSoloAdmin() throws Exception {
        String cajero = login("t_cajero", PASSWORD);
        String almacenero = login("t_almacen", PASSWORD);
        mockMvc.perform(get("/api/caja/sesiones").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/caja/abrir").header("Authorization", "Bearer " + almacenero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/caja/sesiones").header("Authorization", "Bearer " + login("t_admin", PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sunat/envios").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isForbidden());
    }

    @Test
    void losTotalesDeVentasSonSoloDelAdminPeroElCajeroListaLasSuyas() throws Exception {
        String cajero = login("t_cajero", PASSWORD);
        mockMvc.perform(get("/api/ventas/totales").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ventas").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventas/totales").header("Authorization", "Bearer " + login("t_admin", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void cerrarSesionInvalidaElTokenEnElServidor() throws Exception {
        String token = login("t_cajero", PASSWORD);
        mockMvc.perform(post("/api/auth/cerrar-sesion").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        // El mismo token, aunque no haya vencido, ya no sirve.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void losDatosDeClientesSonDeCajaYAdminYElCajeroSoloBusca() throws Exception {
        mockMvc.perform(get("/api/clientes").header("Authorization", "Bearer " + login("t_almacen", PASSWORD)))
                .andExpect(status().isForbidden());
        String cajero = login("t_cajero", PASSWORD);
        // Sin búsqueda no puede listar toda la clientela.
        mockMvc.perform(get("/api/clientes?size=200").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("al menos 3")));
        mockMvc.perform(get("/api/clientes?busqueda=var&size=200").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(get("/api/clientes?size=200").header("Authorization", "Bearer " + login("t_admin", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void contrasenaDeMasDe72BytesEsUnErrorDeValidacionNoInterno() throws Exception {
        String admin = login("t_admin", PASSWORD);
        String larga = "a" + "ñ".repeat(40) + "1";
        mockMvc.perform(post("/api/usuarios").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t_largo\",\"nombre\":\"Largo\",\"rol\":\"CAJERO\",\"passwordTemporal\":\"" + larga + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("demasiado larga")));
        intentarLogin("t_admin", larga).andExpect(status().isBadRequest());
    }

    @Test
    void unCuerpoEnormeSeRechazaSinLeerlo() throws Exception {
        byte[] enorme = ("{\"nombre\":\"" + "A".repeat(2 * 1024 * 1024) + "\"}").getBytes();
        mockMvc.perform(post("/api/clientes").header("Authorization", "Bearer " + login("t_cajero", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(enorme))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void lasRespuestasLlevanCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + login("t_cajero", PASSWORD)))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        // El PDF público se ve en el navegador: sin la CSP de la API, pero sin Referer ni marcos.
        mockMvc.perform(get("/api/publico/comprobantes/00000000-0000-0000-0000-000000000000/pdf"))
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void laMarcaSeVeSinSesionYSoloExponeElNombre() throws Exception {
        mockMvc.perform(get("/api/publico/marca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").isNotEmpty())
                .andExpect(jsonPath("$.configurada").isBoolean())
                .andExpect(jsonPath("$.ruc").doesNotExist())
                .andExpect(jsonPath("$.direccionFiscal").doesNotExist());
        // Solo lectura: no abre la puerta a modificar la empresa sin sesión.
        mockMvc.perform(post("/api/publico/marca")).andExpect(status().isUnauthorized());
    }

    @Test
    void elErrorDeUnaDescargaLlegaComoJsonAunqueSePidaUnArchivo() throws Exception {
        // El cliente del front pide los PDF con Accept: application/octet-stream; si fallan, debe llegar
        // el error real (404 con mensaje), no un 500 vacío por no poder negociar JSON.
        mockMvc.perform(get("/api/ventas/999999/pdf").accept(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Authorization", "Bearer " + login("t_cajero", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.message").value("Comprobante no encontrado: 999999"));
    }

    @Test
    void lasAlertasSonSoloDelAdmin() throws Exception {
        mockMvc.perform(get("/api/alertas").header("Authorization", "Bearer " + login("t_cajero", PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/alertas").header("Authorization", "Bearer " + login("t_admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticas").isNumber());
    }

    @Test
    void movimientosDeInventarioSoloParaAlmacenYAdmin() throws Exception {
        String cajero = login("t_cajero", PASSWORD);
        String almacenero = login("t_almacen", PASSWORD);
        mockMvc.perform(get("/api/inventario/stock").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/inventario/movimientos").header("Authorization", "Bearer " + cajero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/inventario/movimientos").header("Authorization", "Bearer " + almacenero)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
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
