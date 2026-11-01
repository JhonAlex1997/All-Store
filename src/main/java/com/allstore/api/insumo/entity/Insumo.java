package com.allstore.api.insumo.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Material de producción: tela (en metros), cierres, botones, remaches, etiquetas, hilo... */
@Getter
@Setter
@Entity
@Table(name = "insumo")
public class Insumo extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Catálogo 03 SUNAT: MTR (metro), NIU (unidad), KGM (kilo), CEN (ciento)... */
    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida;

    /** Costo unitario SIN IGV, opcional (a mano o el de la última compra, si se pide). */
    @Column(precision = 12, scale = 4)
    private BigDecimal costo;

    @Column(nullable = false)
    private boolean activo = true;
}
