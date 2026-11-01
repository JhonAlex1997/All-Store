package com.allstore.api.produccion.entity;

import com.allstore.api.compra.entity.Proveedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Una etapa de la orden: quién la hace (taller propio o externo), cuándo, cuántas salen y cuánto cuesta. */
@Getter
@Setter
@Entity
@Table(name = "orden_produccion_etapa")
public class OrdenEtapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", updatable = false)
    private OrdenProduccion orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private Etapa etapa;

    @Column(name = "orden_etapa", nullable = false, updatable = false)
    private int ordenEtapa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoEtapa estado = EstadoEtapa.PENDIENTE;

    /** Taller externo; null = taller propio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(name = "fecha_inicio")
    private OffsetDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private OffsetDateTime fechaFin;

    private Integer unidades;

    @Column(name = "costo_servicio", precision = 12, scale = 2)
    private BigDecimal costoServicio;

    @Column(length = 250)
    private String observacion;
}
