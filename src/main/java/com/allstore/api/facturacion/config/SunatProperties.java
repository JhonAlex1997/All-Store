package com.allstore.api.facturacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuración del envío a SUNAT ({@code app.sunat.*}). Usuario, clave SOL y certificado son
 * secretos: en producción van en variables de entorno, nunca en el código.
 *
 * @param habilitado       si el proceso en segundo plano envía los comprobantes pendientes
 * @param entorno          BETA (pruebas) o PRODUCCION
 * @param url              para usar otra dirección (vacía = la del entorno)
 * @param usuarioSol       usuario secundario SOL con permiso de envío (sin el RUC delante)
 * @param claveSol         clave de ese usuario
 * @param certificadoRuta  archivo .p12/.pfx del certificado digital con el que se firma
 * @param certificadoClave clave del certificado
 * @param intervaloEnvioMs cada cuánto revisa los pendientes
 * @param maxIntentos      intentos antes de marcar el envío como ERROR
 */
@ConfigurationProperties(prefix = "app.sunat")
public record SunatProperties(
        @DefaultValue("false") boolean habilitado,
        @DefaultValue("BETA") EntornoSunat entorno,
        String url,
        String usuarioSol,
        String claveSol,
        String certificadoRuta,
        String certificadoClave,
        @DefaultValue("30000") long intervaloEnvioMs,
        @DefaultValue("8") int maxIntentos
) {
    public String urlEfectiva() {
        return StringUtils.hasText(url) ? url : entorno.getUrlFacturacion();
    }

    public boolean tieneCertificado() {
        return StringUtils.hasText(certificadoRuta);
    }
}
