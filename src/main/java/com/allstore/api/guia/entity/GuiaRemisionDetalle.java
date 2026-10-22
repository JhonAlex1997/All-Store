package com.allstore.api.guia.entity;

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

/** Prenda trasladada, con copia de su código y descripción al emitir. Sin auditoría propia (está en la guía). */
@Getter
@Setter
@Entity
@Table(name = "guia_remision_detalle")
public class GuiaRemisionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guia_id")
    private GuiaRemision guia;

    @Column(nullable = false)
    private int item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id")
    private ProductoVariante variante;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 250)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida;

    @Column(nullable = false)
    private int cantidad;
}
