package com.allstore.api.producto.entity;

import com.allstore.api.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de prenda (ej. "Jean Mom Fit Clásico"). No se vende directamente: lo que se vende y
 * se cuenta en inventario son sus {@link ProductoVariante} (cada talla + color).
 */
@Getter
@Setter
@Entity
@Table(name = "producto")
public class Producto extends BaseEntity {

    public static final String UNIDAD_MEDIDA_UNIDAD = "NIU";
    public static final String AFECTACION_GRAVADO = "10";

    /** Código del modelo; no se puede cambiar porque forma parte del SKU de las variantes. */
    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Precio al público con IGV incluido. */
    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    /** Catálogo 03 SUNAT. */
    @Column(name = "unidad_medida", nullable = false, length = 3)
    private String unidadMedida = UNIDAD_MEDIDA_UNIDAD;

    /** Catálogo 07 SUNAT. */
    @Column(name = "tipo_afectacion_igv", nullable = false, length = 2)
    private String tipoAfectacionIgv = AFECTACION_GRAVADO;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ProductoVariante> variantes = new ArrayList<>();

    public void agregarVariante(ProductoVariante variante) {
        variante.setProducto(this);
        variantes.add(variante);
    }

    public boolean tieneVariante(Long tallaId, Long colorId) {
        return variantes.stream().anyMatch(v ->
                v.getTalla().getId().equals(tallaId) && v.getColor().getId().equals(colorId));
    }
}
