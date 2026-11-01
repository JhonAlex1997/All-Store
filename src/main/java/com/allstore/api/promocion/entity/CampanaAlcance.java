package com.allstore.api.promocion.entity;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una categoría, un modelo o una prenda a la que aplica la campaña (solo uno de los tres). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campana_alcance")
public class CampanaAlcance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campana_id", updatable = false)
    private Campana campana;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "variante_id")
    private Long varianteId;

    public CampanaAlcance(Long categoriaId, Long productoId, Long varianteId) {
        this.categoriaId = categoriaId;
        this.productoId = productoId;
        this.varianteId = varianteId;
    }
}
