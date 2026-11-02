package com.allstore.api.empresa;

import com.allstore.api.seguridad.entity.Rol;
import com.allstore.api.seguridad.service.UsuarioService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logo de la tienda: subida validada, re-codificada a PNG, pública para el login y solo editable por el admin. */
@SpringBootTest
@Transactional
class LogoIntegrationTest {

    private static final String PASSWORD = "Prueba2026x";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UsuarioService usuarioService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuarioService.registrar("t_admin", "Admin Prueba", Rol.ADMIN, PASSWORD, false);
        usuarioService.registrar("t_cajero", "Cajero Prueba", Rol.CAJERO, PASSWORD, false);
    }

    @Test
    void elAdminSubeUnJpgYSeGuardaComoPngReducidoYPublico() throws Exception {
        String respuesta = mockMvc.perform(subir(imagen("jpg", 1600, 1000), "logo.jpg", "t_admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoVersion").isNumber())
                .andReturn().getResponse().getContentAsString();
        long version = ((Number) JsonPath.read(respuesta, "$.logoVersion")).longValue();

        mockMvc.perform(get("/api/publico/marca")).andExpect(jsonPath("$.logoVersion").value(version));

        byte[] png = mockMvc.perform(get("/api/publico/marca/logo").param("v", String.valueOf(version)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(png[1]).isEqualTo((byte) 'P');
        // Reducido a 800 px de lado mayor, conservando la proporción.
        assertThat(leida.getWidth()).isEqualTo(800);
        assertThat(leida.getHeight()).isEqualTo(500);
    }

    @Test
    void conservaLaTransparenciaDeUnPng() throws Exception {
        mockMvc.perform(subir(imagen("png", 200, 100), "logo.png", "t_admin")).andExpect(status().isOk());
        byte[] png = mockMvc.perform(get("/api/publico/marca/logo")).andReturn().getResponse().getContentAsByteArray();
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(leida.getColorModel().hasAlpha()).isTrue();
        assertThat(leida.getRGB(0, 0) >>> 24).as("esquina transparente").isZero();
    }

    @Test
    void rechazaLoQueNoEsUnaImagenAunqueSeLlameLogoPng() throws Exception {
        byte[] falso = "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(subir(falso, "logo.png", "t_admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El logo debe ser una imagen PNG o JPG"));
        // Cabecera PNG válida pero contenido roto.
        byte[] roto = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        mockMvc.perform(subir(roto, "logo.png", "t_admin")).andExpect(status().isBadRequest());
    }

    @Test
    void soloElAdminCambiaElLogoYSinSesionNoSePuede() throws Exception {
        mockMvc.perform(subir(imagen("png", 50, 50), "logo.png", "t_cajero")).andExpect(status().isForbidden());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/empresa/logo")
                        .file(new MockMultipartFile("archivo", "logo.png", "image/png", imagen("png", 50, 50))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinLogoLaMarcaNoTraeVersionYAlQuitarloDesaparece() throws Exception {
        mockMvc.perform(get("/api/publico/marca")).andExpect(jsonPath("$.logoVersion").doesNotExist());
        mockMvc.perform(get("/api/publico/marca/logo")).andExpect(status().isNotFound());

        mockMvc.perform(subir(imagen("png", 50, 50), "logo.png", "t_admin")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/empresa/logo").header("Authorization", "Bearer " + login("t_admin")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/publico/marca/logo")).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.RequestBuilder subir(byte[] contenido, String nombre, String usuario)
            throws Exception {
        return multipart(HttpMethod.PUT, "/api/empresa/logo")
                .file(new MockMultipartFile("archivo", nombre, "image/png", contenido))
                .header("Authorization", "Bearer " + login(usuario));
    }

    /** Imagen de prueba: rosado en el centro; si es PNG, con las esquinas transparentes. */
    private static byte[] imagen(String formato, int ancho, int alto) throws Exception {
        boolean png = formato.equals("png");
        BufferedImage img = new BufferedImage(ancho, alto, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        if (!png) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
        }
        g.setColor(new Color(244, 196, 205));
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, salida);
        return salida.toByteArray();
    }

    private String login(String usuario) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
