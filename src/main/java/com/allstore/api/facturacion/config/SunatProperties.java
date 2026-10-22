package com.allstore.api.facturacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Configuración del envío a SUNAT ({@code app.sunat.*}). Usuario, clave SOL, certificado y
 * credenciales de la API de guías son secretos: en producción van en variables de entorno, nunca
 * en el código.
 *
 * @param habilitado       si el proceso en segundo plano envía los documentos pendientes
 * @param entorno          BETA (pruebas) o PRODUCCION
 * @param url              para usar otra dirección del servicio de comprobantes (vacía = la del entorno)
 * @param usuarioSol       usuario secundario SOL con permiso de envío (sin el RUC delante)
 * @param claveSol         clave de ese usuario
 * @param certificadoRuta  archivo .p12/.pfx del certificado digital con el que se firma
 * @param certificadoClave clave del certificado
 * @param intervaloEnvioMs cada cuánto revisa los pendientes
 * @param maxIntentos      intentos antes de marcar el envío como ERROR
 * @param gre              API REST de la guía de remisión (opcional)
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
        @DefaultValue("8") int maxIntentos,
        Gre gre
) {
    /**
     * Acceso a la API REST de guías ({@code app.sunat.gre.*}). Las credenciales se generan en el
     * menú SOL (Empresas > Credenciales de API SUNAT) y autentican junto con el usuario SOL.
     *
     * @param clientId     identificador de la aplicación registrada en SOL
     * @param clientSecret su clave
     * @param urlSeguridad para usar otro servicio de token (vacía = el del entorno)
     * @param urlApi       para usar otro servicio de guías (vacía = el del entorno)
     */
    public record Gre(String clientId, String clientSecret, String urlSeguridad, String urlApi) {
    }

    public String urlEfectiva() {
        return StringUtils.hasText(url) ? url : entorno.getUrlFacturacion();
    }

    public boolean tieneCertificado() {
        return StringUtils.hasText(certificadoRuta);
    }

    public String urlGreSeguridad() {
        return gre != null && StringUtils.hasText(gre.urlSeguridad()) ? gre.urlSeguridad() : entorno.getUrlGreSeguridad();
    }

    public String urlGreApi() {
        return gre != null && StringUtils.hasText(gre.urlApi()) ? gre.urlApi() : entorno.getUrlGreApi();
    }

    /** Si hay todo lo necesario para enviar guías: credenciales de la API, usuario SOL y direcciones. */
    public boolean greConfigurada() {
        return gre != null && StringUtils.hasText(gre.clientId()) && StringUtils.hasText(gre.clientSecret())
                && StringUtils.hasText(usuarioSol) && StringUtils.hasText(urlGreSeguridad()) && StringUtils.hasText(urlGreApi());
    }
}
