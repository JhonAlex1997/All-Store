package com.allstore.api.empresa;

import com.allstore.api.empresa.dto.EmpresaRequest;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.TipoEstablecimiento;
import com.allstore.api.empresa.repository.EstablecimientoRepository;
import com.allstore.api.empresa.service.EmpresaService;
import com.allstore.api.empresa.service.EstablecimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** El local 0000 es el domicilio fiscal: su dirección siempre es la de la empresa. */
@SpringBootTest
@Transactional
class DomicilioFiscalIntegrationTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private EstablecimientoService establecimientoService;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Test
    void alCambiarLaDireccionFiscalSeActualizaElLocal0000() {
        Long id = domicilioFiscal("Dirección vieja", "150101");
        Long almacen = establecimientoService.crear(new EstablecimientoRequest("0007", "Almacén prueba",
                TipoEstablecimiento.ALMACEN, "Av. Almacén 1", "150103", true)).id();

        empresaService.guardar(empresa("Jr. Mariscal Agustín Gamarra N° 499 Tienda 218", "150115"));

        Establecimiento local = establecimientoRepository.findById(id).orElseThrow();
        assertThat(local.getDireccion()).isEqualTo("Jr. Mariscal Agustín Gamarra N° 499 Tienda 218");
        assertThat(local.getUbigeo()).isEqualTo("150115");
        // Los demás locales conservan su propia dirección.
        assertThat(establecimientoRepository.findById(almacen).orElseThrow().getDireccion()).isEqualTo("Av. Almacén 1");
    }

    @Test
    void elLocal0000NoPuedeTenerOtraDireccionQueLaFiscal() {
        empresaService.guardar(empresa("Jr. Gamarra 499 Tienda 218", "150115"));
        Long id = domicilioFiscal("Jr. Gamarra 499 Tienda 218", "150115");

        establecimientoService.actualizar(id, new EstablecimientoRequest(Establecimiento.CODIGO_DOMICILIO_FISCAL,
                "Tienda principal", TipoEstablecimiento.TIENDA, "Otra dirección", "150101", true));

        Establecimiento local = establecimientoRepository.findById(id).orElseThrow();
        assertThat(local.getDireccion()).isEqualTo("Jr. Gamarra 499 Tienda 218");
        assertThat(local.getUbigeo()).isEqualTo("150115");
        assertThat(local.getNombre()).as("el nombre sí se puede cambiar").isEqualTo("Tienda principal");
    }

    /** El local 0000 de la base de pruebas (o uno nuevo si no existe), con la dirección indicada. */
    private Long domicilioFiscal(String direccion, String ubigeo) {
        return establecimientoRepository.findByCodigo(Establecimiento.CODIGO_DOMICILIO_FISCAL)
                .map(l -> {
                    l.setDireccion(direccion);
                    l.setUbigeo(ubigeo);
                    return l.getId();
                })
                .orElseGet(() -> establecimientoService.crear(new EstablecimientoRequest(
                        Establecimiento.CODIGO_DOMICILIO_FISCAL, "Principal", TipoEstablecimiento.TIENDA,
                        direccion, ubigeo, true)).id());
    }

    private static EmpresaRequest empresa(String direccion, String ubigeo) {
        return new EmpresaRequest("20610224602", "CONSORCIO TEXTIL C&J S.A.C.", "KISSME JEANS", direccion, ubigeo,
                null, null, null);
    }
}
