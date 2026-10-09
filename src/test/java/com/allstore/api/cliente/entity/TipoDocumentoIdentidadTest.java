package com.allstore.api.cliente.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoDocumentoIdentidadTest {

    @Test
    void rucDeEmpresasRealesEsValido() {
        assertThat(TipoDocumentoIdentidad.RUC.validar("20131312955")).isEmpty(); // SUNAT
        assertThat(TipoDocumentoIdentidad.RUC.validar("20100070970")).isEmpty();
    }

    @Test
    void rucDePersonaNaturalEsValido() {
        assertThat(TipoDocumentoIdentidad.RUC.validar("10456789124")).isEmpty();
    }

    @Test
    void rucConDigitoVerificadorErradoEsRechazado() {
        assertThat(TipoDocumentoIdentidad.RUC.validar("20131312956")).contains("El RUC 20131312956 no es válido");
    }

    @Test
    void rucConPrefijoInexistenteEsRechazado() {
        assertThat(TipoDocumentoIdentidad.esRucValido("30131312955")).isFalse();
    }

    @Test
    void rucConLongitudIncorrectaEsRechazado() {
        assertThat(TipoDocumentoIdentidad.RUC.validar("2013131295")).contains("El RUC debe tener 11 dígitos");
    }

    @Test
    void dniDebeTener8Digitos() {
        assertThat(TipoDocumentoIdentidad.DNI.validar("45678912")).isEmpty();
        assertThat(TipoDocumentoIdentidad.DNI.validar("4567891")).contains("El DNI debe tener 8 dígitos");
        assertThat(TipoDocumentoIdentidad.DNI.validar("4567891A")).contains("El DNI debe tener 8 dígitos");
    }

    @Test
    void codigoSunatSeConvierteEnTipo() {
        assertThat(TipoDocumentoIdentidad.desdeCodigoSunat("6")).isEqualTo(TipoDocumentoIdentidad.RUC);
        assertThat(TipoDocumentoIdentidad.DNI.getCodigoSunat()).isEqualTo("1");
    }
}
