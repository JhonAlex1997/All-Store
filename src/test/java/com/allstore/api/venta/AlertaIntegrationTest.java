package com.allstore.api.venta;

import com.allstore.api.alerta.dto.Alerta;
import com.allstore.api.alerta.dto.Alerta.Nivel;
import com.allstore.api.alerta.dto.Alerta.Tipo;
import com.allstore.api.alerta.service.AlertaService;
import com.allstore.api.alerta.service.AvisoAlertas;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.facturacion.repository.EnvioSunatRepository;
import com.allstore.api.facturacion.repository.ResumenSunatRepository;
import com.allstore.api.facturacion.service.BajaSunatService;
import com.allstore.api.facturacion.service.EnvioSunatService;
import com.allstore.api.facturacion.service.ServicioEnvioSunat;
import com.allstore.api.guia.dto.GuiaRemisionRequest;
import com.allstore.api.guia.dto.GuiaRemisionResponse;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.guia.service.GuiaRemisionService;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import com.allstore.api.venta.dto.AnularVentaRequest;
import com.allstore.api.venta.dto.VentaResponse;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Alertas para el administrador, con SUNAT simulado. Los estados que tardarían días (plazos,
 * reintentos agotados) se preparan directamente en la base.
 */
class AlertaIntegrationTest extends TiendaDePrueba {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ServicioEnvioSunat.Respuesta ACEPTADA =
            new ServicioEnvioSunat.Respuesta(true, "0", "Aceptada", List.of(), new byte[]{1});
    private static final ServicioEnvioSunat.Respuesta RECHAZADA =
            new ServicioEnvioSunat.Respuesta(true, "2017", "El RUC del receptor no es valido", List.of(), new byte[]{1});

    @Autowired private AlertaService alertaService;
    @Autowired private AvisoAlertas avisoAlertas;
    @Autowired private EmpresaService empresaService;
    @Autowired private EnvioSunatService envioSunatService;
    @Autowired private EnvioSunatRepository envioRepository;
    @Autowired private ResumenSunatRepository resumenRepository;
    @Autowired private BajaSunatService bajaSunatService;
    @Autowired private GuiaRemisionService guiaService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @MockitoBean private ServicioEnvioSunat servicioEnvio;

    @BeforeEach
    void empresaConCorreo() {
        if (empresaService.buscarEntidad().isEmpty()) {
            empresaService.guardar(new EmpresaRequest("20000000001", "EMPRESA PRUEBA SAC", "Jeans Prueba",
                    "Jr. Prueba 1", "150115", null, "dueno@prueba.pe", null));
        }
    }

    @Test
    void enPruebasAvisaQueElEnvioASunatEstaDesactivado() {
        assertThat(alertaService.alertas().alertas())
                .anySatisfy(a -> assertThat(a).extracting(Alerta::tipo, Alerta::nivel)
                        .containsExactly(Tipo.SUNAT_DESACTIVADO, Nivel.CRITICA));
    }

    @Test
    void elRechazadoAlertaHastaQueSeReemite() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(RECHAZADA);
        VentaResponse boleta = boleta();
        envioSunatService.enviarAhora(boleta.id(), false);

        assertThat(alerta(Tipo.COMPROBANTE_RECHAZADO, boleta.numero())).hasValueSatisfying(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICA);
            assertThat(a.comprobanteId()).isEqualTo(boleta.id());
            assertThat(a.mensaje()).contains("2017 El RUC del receptor no es valido", "reemítelo con otro número o anúlalo");
        });

        ventaService.reemitir(boleta.id(), null);
        assertThat(alerta(Tipo.COMPROBANTE_RECHAZADO, boleta.numero())).isEmpty();
    }

    @Test
    void sinAceptarAvisaElPlazoDeSunatYCuandoVence() {
        VentaResponse deHoy = boleta();
        VentaResponse deAnteayer = boleta();
        VentaResponse deHaceCinco = boleta();
        atrasarEmision(deAnteayer, 2);
        atrasarEmision(deHaceCinco, 5);
        LocalDate hoy = LocalDate.now(JpaAuditingConfig.ZONA_LIMA);

        assertThat(alerta(Tipo.PLAZO_DE_ENVIO, deHoy.numero())).isEmpty();
        assertThat(alerta(Tipo.PLAZO_DE_ENVIO, deAnteayer.numero())).hasValueSatisfying(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.ADVERTENCIA);
            assertThat(a.mensaje()).contains("SUNAT lo recibe hasta el " + FECHA.format(hoy.plusDays(1)));
        });
        assertThat(alerta(Tipo.PLAZO_DE_ENVIO, deHaceCinco.numero())).hasValueSatisfying(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICA);
            assertThat(a.mensaje()).contains("venció el plazo de envío (hasta el " + FECHA.format(hoy.minusDays(2)) + ")");
        });
    }

    @Test
    void elEnvioQueAgotoSusIntentosEsCritico() {
        VentaResponse boleta = boleta();
        EnvioSunat envio = envioRepository.findByComprobanteId(boleta.id()).orElseThrow();
        envio.setEstado(EstadoSunat.ERROR);
        envio.setIntentos(8);
        envio.setDescripcionRespuesta("Sin comunicación con SUNAT: Read timed out");

        assertThat(alerta(Tipo.ENVIO_EN_ERROR, boleta.numero())).hasValueSatisfying(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICA);
            assertThat(a.mensaje()).contains("tras 8 intentos", "Read timed out", "reenvíalo desde el panel", "SUNAT lo recibe hasta el");
        });
    }

    @Test
    void laBajaRechazadaAlertaHastaQueSeReintenta() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(ACEPTADA);
        VentaResponse boleta = boleta();
        envioSunatService.enviarAhora(boleta.id(), false);
        anulacionService.anular(boleta.id(), new AnularVentaRequest("El cliente desistió"));
        Long resumenId = resumenRepository.ultimaBaja(boleta.id()).orElseThrow().getResumen().getId();
        EnvioSunat baja = envioRepository.findByResumenId(resumenId).orElseThrow();
        baja.setEstado(EstadoSunat.RECHAZADO);
        baja.setCodigoRespuesta("2992");
        baja.setDescripcionRespuesta("Tasa del tributo faltante");

        assertThat(alertas(Tipo.BAJA_RECHAZADA)).singleElement().satisfies(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICA);
            assertThat(a.mensaje()).contains(boleta.numero() + " sigue vigente para SUNAT", "Reintenta la baja");
        });

        bajaSunatService.reintentar(boleta.id());
        assertThat(alertas(Tipo.BAJA_RECHAZADA)).isEmpty();
    }

    @Test
    void laGuiaSinAceptarAvisaElDiaDelTrasladoYEsCriticaSiYaEmpezo() {
        Long serieGuia = serie(TipoComprobante.GUIA_REMISION_REMITENTE, "T991");
        GuiaRemisionResponse guia = guiaService.emitir(new GuiaRemisionRequest(serieGuia, MotivoTraslado.TRASLADO_ESTABLECIMIENTOS,
                null, null, null, almacen, new BigDecimal("5.2"), 1, ModalidadTransporte.PRIVADO, null, true, null, null, null,
                List.of(new ItemCantidadRequest(jeanB, 2))));

        assertThat(alerta(Tipo.GUIA_SIN_ACEPTAR, guia.numero())).hasValueSatisfying(a -> {
            assertThat(a.nivel()).isEqualTo(Nivel.ADVERTENCIA);
            assertThat(a.mensaje()).contains("empieza hoy", "no debe viajar sin una guía aceptada");
        });

        entityManager.flush();
        jdbc.update("update guia_remision set fecha_traslado = fecha_traslado - 1 where id = ?", guia.id());
        entityManager.clear();
        assertThat(alerta(Tipo.GUIA_SIN_ACEPTAR, guia.numero())).hasValueSatisfying(a ->
                assertThat(a.nivel()).isEqualTo(Nivel.CRITICA));
    }

    @Test
    void elResumenLlegaPorCorreoAlDueno() throws Exception {
        when(servicioEnvio.enviar(anyString(), any())).thenReturn(RECHAZADA);
        VentaResponse boleta = boleta();
        envioSunatService.enviarAhora(boleta.id(), false);

        AvisoAlertas.Aviso aviso = avisoAlertas.enviar();

        assertThat(aviso.destinatarios()).containsExactly("dueno@prueba.pe");
        assertThat(aviso.criticas()).isPositive();
        MimeMessage correo = ultimoCorreoDeAlertas();
        assertThat(correo.getSubject()).startsWith("Jeans Prueba: ").contains("alertas críticas");
        assertThat((String) correo.getContent()).contains("CRÍTICAS", "SUNAT rechazó Boleta de venta " + boleta.numero());
    }

    // ---------- Ayudas ----------

    private VentaResponse boleta() {
        return vender(TipoComprobante.BOLETA, serieBoleta, null, List.of(item(jeanA, 1, null)), List.of(efectivo("89.90", null)));
    }

    private void atrasarEmision(VentaResponse venta, int dias) {
        entityManager.flush();
        jdbc.update("update comprobante set fecha_emision = fecha_emision - make_interval(days => ?) where id = ?", dias, venta.id());
        entityManager.clear();
    }

    private List<Alerta> alertas(Tipo tipo) {
        return alertaService.alertas().alertas().stream().filter(a -> a.tipo() == tipo).toList();
    }

    private Optional<Alerta> alerta(Tipo tipo, String documento) {
        return alertas(tipo).stream().filter(a -> documento.equals(a.documento())).findFirst();
    }

    private static MimeMessage ultimoCorreoDeAlertas() throws Exception {
        Path ultimo;
        try (Stream<Path> archivos = Files.list(Path.of("target/correos-test"))) {
            ultimo = archivos.filter(p -> p.getFileName().toString().startsWith("alertas-"))
                    .max(Comparator.comparing(p -> p.toFile().lastModified())).orElseThrow();
        }
        try (InputStream entrada = Files.newInputStream(ultimo)) {
            return new MimeMessage(Session.getInstance(new Properties()), entrada);
        }
    }
}
