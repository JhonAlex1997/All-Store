package com.allstore.api.facturacion.entity;

import com.allstore.api.common.entity.BaseEntity;
import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Comunicación de baja (RA) o resumen diario (RC). Su número es tipo, fecha de generación y un
 * correlativo que vuelve a 1 cada día: RA-20261022-1. Su envío a SUNAT es un {@link EnvioSunat}.
 */
@Getter
@Setter
@Entity
@Table(name = "resumen_sunat")
public class ResumenSunat extends BaseEntity {

    private static final DateTimeFormatter FECHA_NUMERO = DateTimeFormatter.BASIC_ISO_DATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2, updatable = false)
    private TipoResumen tipo;

    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    private LocalDate fechaGeneracion;

    @Column(nullable = false, updatable = false)
    private int correlativo;

    /** Día de emisión de los comprobantes que incluye: SUNAT exige que sean todos del mismo día. */
    @Column(name = "fecha_referencia", nullable = false, updatable = false)
    private LocalDate fechaReferencia;

    @OneToMany(mappedBy = "resumen", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ResumenSunatDetalle> detalles = new ArrayList<>();

    public void agregar(Comprobante comprobante, String motivo) {
        ResumenSunatDetalle detalle = new ResumenSunatDetalle();
        detalle.setResumen(this);
        detalle.setComprobante(comprobante);
        detalle.setMotivo(motivo);
        detalles.add(detalle);
    }

    /** RA-20261022-1 (es también el cbc:ID del XML). */
    public String getNumero() {
        return tipo.name() + "-" + FECHA_NUMERO.format(fechaGeneracion) + "-" + correlativo;
    }
}
