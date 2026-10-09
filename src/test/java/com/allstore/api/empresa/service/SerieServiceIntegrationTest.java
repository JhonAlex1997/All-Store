package com.allstore.api.empresa.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.dto.SerieResponse;
import com.allstore.api.empresa.dto.SerieUpdateRequest;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Corre contra la base configurada en .env. Cada prueba es transaccional y se revierte al
 * terminar, así que no deja datos.
 */
@SpringBootTest
@Transactional
class SerieServiceIntegrationTest {

    @Autowired
    private SerieService serieService;

    @Autowired
    private EstablecimientoService establecimientoService;

    private Long establecimientoId;

    @BeforeEach
    void crearEstablecimiento() {
        establecimientoId = establecimientoService.crear(new EstablecimientoRequest(
                "9999", "Tienda de prueba", TipoEstablecimiento.TIENDA, "Av. Prueba 123", "150101", true)).id();
    }

    @Test
    void asignaNumerosConsecutivosDesdeElCorrelativoInicial() {
        SerieResponse serie = serieService.crear(new SerieCreateRequest(
                TipoComprobante.BOLETA, "b999", establecimientoId, 123L));

        assertThat(serie.codigo()).isEqualTo("B999");
        assertThat(serie.siguienteNumero()).isEqualTo("B999-00000124");

        NumeroComprobante primero = serieService.asignarSiguienteNumero(serie.id(), TipoComprobante.BOLETA);
        NumeroComprobante segundo = serieService.asignarSiguienteNumero(serie.id(), TipoComprobante.BOLETA);

        assertThat(primero.numero()).isEqualTo("B999-124");
        assertThat(segundo.numeroImpreso()).isEqualTo("B999-00000125");
    }

    @Test
    void noAsignaNumeroDeUnaSerieDeOtroTipo() {
        SerieResponse serie = serieService.crear(new SerieCreateRequest(
                TipoComprobante.FACTURA, "F999", establecimientoId, null));

        assertThatThrownBy(() -> serieService.asignarSiguienteNumero(serie.id(), TipoComprobante.BOLETA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("es de factura");
    }

    @Test
    void elCorrelativoNoPuedeBajar() {
        SerieResponse serie = serieService.crear(new SerieCreateRequest(
                TipoComprobante.BOLETA, "B998", establecimientoId, 50L));

        assertThatThrownBy(() -> serieService.actualizar(serie.id(),
                new SerieUpdateRequest(establecimientoId, 10L, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no puede bajar");
    }

    @Test
    void serieInactivaNoEmite() {
        SerieResponse serie = serieService.crear(new SerieCreateRequest(
                TipoComprobante.BOLETA, "B997", establecimientoId, null));
        serieService.actualizar(serie.id(), new SerieUpdateRequest(establecimientoId, null, false));

        assertThatThrownBy(() -> serieService.asignarSiguienteNumero(serie.id(), TipoComprobante.BOLETA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactiva");
    }
}
