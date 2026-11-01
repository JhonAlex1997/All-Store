package com.allstore.api.inventario.entity;

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

import java.time.OffsetDateTime;

/**
 * Una prenda del conteo. La diferencia se mide contra el stock del sistema en el momento en que se
 * contó ({@code stockAlContar}), no contra el del cierre: lo vendido después no es faltante.
 */
@Getter
@Setter
@Entity
@Table(name = "conteo_detalle")
public class ConteoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conteo_id", updatable = false)
    private ConteoInventario conteo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", updatable = false)
    private ProductoVariante variante;

    @Column(name = "stock_inicial", nullable = false)
    private int stockInicial;

    private Integer contado;

    @Column(name = "stock_al_contar")
    private Integer stockAlContar;

    @Column(name = "fecha_conteo")
    private OffsetDateTime fechaConteo;

    /** + sobrante, - faltante, aplicado al cerrar. */
    private Integer ajuste;

    /** Contado menos el stock al contar; null si aún no se contó. */
    public Integer diferencia() {
        return contado == null ? null : contado - stockAlContar;
    }
}
