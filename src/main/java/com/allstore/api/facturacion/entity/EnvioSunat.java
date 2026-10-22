package com.allstore.api.facturacion.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Envío a SUNAT de un documento electrónico (un comprobante o un resumen), con sus archivos y la
 * respuesta. Exactamente uno de los dos está presente.
 */
@Getter
@Setter
@Entity
@Table(name = "envio_sunat")
public class EnvioSunat extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_id", updatable = false)
    private Comprobante comprobante;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resumen_id", updatable = false)
    private ResumenSunat resumen;

    /** Resúmenes: número de ticket con el que se consulta el resultado. */
    @Column(length = 60)
    private String ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoSunat estado = EstadoSunat.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntornoSunat entorno;

    @Column(name = "nombre_archivo", length = 60)
    private String nombreArchivo;

    @Column(name = "xml_firmado", columnDefinition = "text")
    private String xmlFirmado;

    @Column(length = 100)
    private String hash;

    @Column(columnDefinition = "bytea")
    private byte[] cdr;

    @Column(name = "codigo_respuesta", length = 10)
    private String codigoRespuesta;

    @Column(name = "descripcion_respuesta", length = 1000)
    private String descripcionRespuesta;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento")
    private OffsetDateTime proximoIntento;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_respuesta")
    private OffsetDateTime fechaRespuesta;

    public boolean tieneXml() {
        return xmlFirmado != null;
    }

    /** Ej. "Boleta de venta B001-00000015" o "Comunicación de baja RA-20261022-1". */
    public String getDocumento() {
        return comprobante != null
                ? comprobante.getTipoComprobante().getDescripcion() + " " + comprobante.getNumeroImpreso()
                : resumen.getTipo().getDescripcion() + " " + resumen.getNumero();
    }
}
