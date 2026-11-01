package com.allstore.api.monitoreo;

import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.AlertaService;
import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.service.UsuarioService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monitoreo de punta a punta: salud, estado del sistema, métricas, códigos de solicitud y alertas. */
@SpringBootTest(properties = "app.seguridad.metricas-token=" + MonitoreoIntegrationTest.TOKEN)
@Transactional
class MonitoreoIntegrationTest {

    static final String TOKEN = "token-de-metricas-de-prueba";
    private static final String PASSWORD = "Prueba2026x";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioService usuarioService;
    @Autowired private RegistroSolicitudes registroSolicitudes;
    @Autowired private RegistroErrores registroErrores;
    @Autowired private AlertaService alertaService;

    private MockMvc mockMvc;
    private String admin;
    private String cajero;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(registroSolicitudes).apply(springSecurity()).build();
        usuarioService.registrar("m_admin", "Admin Monitoreo", Rol.ADMIN, PASSWORD, false);
        usuarioService.registrar("m_cajero", "Cajero Monitoreo", Rol.CAJERO, PASSWORD, false);
        admin = login("m_admin");
        cajero = login("m_cajero");
    }

    @Test
    void laSaludEsPublicaPeroSinDetalleYElAdminVeElDetalle() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void elEstadoDelSistemaEsSoloDelAdministrador() throws Exception {
        mockMvc.perform(get("/api/sistema/estado").header("Authorization", "Bearer " + cajero))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sistema/estado").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").exists())
                .andExpect(jsonPath("$.baseDatos.disponible").value(true))
                .andExpect(jsonPath("$.baseDatos.tamanoMb").isNumber())
                .andExpect(jsonPath("$.disco.libreGb").isNumber())
                .andExpect(jsonPath("$.procesos", hasSize(3)))
                .andExpect(jsonPath("$.colas.sunatPendientes").isNumber())
                .andExpect(jsonPath("$.tiempoActivo").value(containsString("min")));
    }

    @Test
    void lasMetricasDePrometheusPidenSuClaveYLasDeActuatorSonDelAdmin() throws Exception {
        mockMvc.perform(get(AccesoMetricas.RUTA)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
        mockMvc.perform(get(AccesoMetricas.RUTA).header("Authorization", basica("otra-clave-cualquiera")))
                .andExpect(status().isUnauthorized());
        // Ni siquiera un administrador con su sesión: Prometheus usa su propia clave.
        mockMvc.perform(get(AccesoMetricas.RUTA).header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(AccesoMetricas.RUTA).header("Authorization", basica(TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("allstore_sunat_envios")))
                .andExpect(content().string(containsString("allstore_cajas_abiertas")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));

        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + cajero)).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics/allstore.errores.internos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void cadaRespuestaLlevaSuCodigoDeSolicitudTambienLosErrores() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + admin))
                .andExpect(header().string(RegistroSolicitudes.CABECERA, matchesPattern("[0-9a-f]{16}")));
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.solicitud").value(matchesPattern("[0-9a-f]{16}")));
        // El código que manda la caja (o Nginx) se conserva: une su registro con el del servidor.
        mockMvc.perform(get("/api/ventas/999999").header("Authorization", "Bearer " + admin)
                        .header(RegistroSolicitudes.CABECERA, "caja-01-7f3a9c"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RegistroSolicitudes.CABECERA, "caja-01-7f3a9c"))
                .andExpect(jsonPath("$.solicitud").value("caja-01-7f3a9c"));
    }

    @Test
    void unErrorInternoSeVeEnElPanelYEnLasAlertas() throws Exception {
        registroErrores.solicitud("POST", "/api/ventas", new IllegalStateException("conexión perdida con la base"));

        mockMvc.perform(get("/api/sistema/errores").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("POST /api/ventas"))
                .andExpect(jsonPath("$[0].mensaje").value("conexión perdida con la base"));
        mockMvc.perform(get("/api/sistema/estado").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.estado").value(not("OK")))
                .andExpect(jsonPath("$.errores.ultimo.origen").value("POST /api/ventas"));
        assertThat(alertaService.alertas().alertas()).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo(Tipo.ERRORES_INTERNOS);
            assertThat(a.mensaje()).contains("POST /api/ventas");
        });
    }

    private static String basica(String clave) {
        return "Basic " + Base64.getEncoder().encodeToString(("metricas:" + clave).getBytes(StandardCharsets.UTF_8));
    }

    private String login(String usuario) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
