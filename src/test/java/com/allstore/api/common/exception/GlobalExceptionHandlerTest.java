package com.allstore.api.common.exception;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
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
    void errorInesperadoNoExponeDetalles() throws Exception {
        mockMvc.perform(get("/test/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno en el servidor"));
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
