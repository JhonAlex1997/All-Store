package com.allstore.api.cliente.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consulta de RUC (padrón de SUNAT) y DNI (RENIEC) para llenar los datos del cliente
 * ({@code app.consulta-documentos.*}). RENIEC no tiene un servicio público gratuito: se usa un
 * proveedor de pago. El número consultado se envía a ese proveedor.
 *
 * @param proveedor  DESACTIVADO (por defecto: los datos se escriben a mano) o DECOLECTA
 * @param token      clave del proveedor (secreto: va en una variable de entorno)
 * @param url        dirección del proveedor
 * @param horasCache cuánto se recuerda una consulta: el mismo número no se vuelve a pagar
 */
@ConfigurationProperties(prefix = "app.consulta-documentos")
public record ConsultaDocumentosProperties(
        @DefaultValue("DESACTIVADO") Proveedor proveedor,
        String token,
        @DefaultValue("https://api.decolecta.com") String url,
        @DefaultValue("24") int horasCache
) {
    public enum Proveedor {
        DESACTIVADO,
        DECOLECTA
    }

    public boolean habilitada() {
        return proveedor != Proveedor.DESACTIVADO && token != null && !token.isBlank();
    }
}
