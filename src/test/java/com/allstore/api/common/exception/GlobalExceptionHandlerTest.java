package com.allstore.api.common.exception;

import com.allstore.api.monitoreo.RegistroErrores;
import com.allstore.api.monitoreo.RegistroSolicitudes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final RegistroErrores registroErrores = new RegistroErrores(new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(registroErrores))
                .addFilters(new RegistroSolicitudes())
                .build();
    }

    @Test
    void businessExceptionResponde400ConMensaje() throws Exception {
        mockMvc.perform(get("/test/negocio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stock insuficiente"))
                .andExpect(jsonPath("$.path").value("/test/negocio"));
    }

    @Test
    void resourceNotFoundResponde404() throws Exception {
        mockMvc.perform(get("/test/producto/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Producto no encontrado: 99"));
    }

    @Test
    void validacionUsaElPrimerCampoComoMensaje() throws Exception {
        mockMvc.perform(post("/test/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.details.nombre").value("El nombre es obligatorio"));
    }

    @Test
    void errorInesperadoNoExponeDetallesYDaElCodigoParaBuscarloEnElLog() throws Exception {
        mockMvc.perform(get("/test/falla").header(RegistroSolicitudes.CABECERA, "caja-1-abc123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RegistroSolicitudes.CABECERA, "caja-1-abc123"))
                .andExpect(jsonPath("$.solicitud").value("caja-1-abc123"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno en el servidor (código caja-1-abc123). "
                        + "Si se repite, repórtalo con ese código"));
        // Queda en el registro de errores recientes, con el detalle que la respuesta no muestra.
        assertThat(registroErrores.recientes()).singleElement().satisfies(e -> {
            assertThat(e.solicitud()).isEqualTo("caja-1-abc123");
            assertThat(e.origen()).isEqualTo("GET /test/falla");
            assertThat(e.mensaje()).isEqualTo("detalle interno");
        });
    }

    @Test
    void unCodigoRecibidoInvalidoSeReemplazaPorUnoPropio() throws Exception {
        mockMvc.perform(get("/test/negocio").header(RegistroSolicitudes.CABECERA, "<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RegistroSolicitudes.CABECERA, org.hamcrest.Matchers.matchesPattern("[0-9a-f]{16}")))
                .andExpect(jsonPath("$.solicitud").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{16}")));
    }

    record Solicitud(@NotBlank(message = "El nombre es obligatorio") String nombre) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/negocio")
        void negocio() {
            throw new BusinessException("Stock insuficiente");
        }

        @GetMapping("/test/producto/{id}")
        void producto(@PathVariable Long id) {
            throw new ResourceNotFoundException("Producto", id);
        }

        @PostMapping("/test/validar")
        void validar(@Valid @RequestBody Solicitud solicitud) {
        }

        @GetMapping("/test/falla")
        void falla() {
            throw new IllegalStateException("detalle interno");
        }
    }
}
