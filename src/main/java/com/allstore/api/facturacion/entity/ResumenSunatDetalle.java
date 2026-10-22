package com.allstore.api.facturacion.entity;

import com.allstore.api.venta.entity.Comprobante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Un comprobante incluido en un resumen. No extiende BaseEntity: la auditoría está en el resumen. */
@Getter
@Setter
@Entity
@Table(name = "resumen_sunat_detalle")
public class ResumenSunatDetalle {

    /** Largo máximo que SUNAT admite para el motivo de la baja. */
    public static final int MOTIVO_MAXIMO = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resumen_id")
    private ResumenSunat resumen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprobante_id")
    private Comprobante comprobante;

    @Column(nullable = false, length = MOTIVO_MAXIMO)
    private String motivo;
}
