package com.allstore.api.produccion.entity;

import com.allstore.api.producto.entity.ProductoVariante;
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

/** Cuántas prendas de una talla y color se planificaron y cuántas salieron. */
@Getter
@Setter
@Entity
@Table(name = "orden_produccion_talla")
public class OrdenTalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", updatable = false)
    private OrdenProduccion orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", updatable = false)
    private ProductoVariante variante;

    @Column(name = "cantidad_planificada", nullable = false)
    private int cantidadPlanificada;

    @Column(name = "cantidad_terminada")
    private Integer cantidadTerminada;
}
