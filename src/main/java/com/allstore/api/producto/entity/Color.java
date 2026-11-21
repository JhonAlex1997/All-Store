package com.allstore.api.producto.entity;

import com.allstore.api.common.entity.EntidadVersionada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "color")
public class Color extends EntidadVersionada {

    @Column(nullable = false, length = 50)
    private String nombre;

    /** Abreviatura usada en el SKU de las variantes (ej. AZU). */
    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false)
    private boolean activo = true;
}
