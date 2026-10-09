package com.allstore.api.producto.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.dto.ProductoResumenResponse;
import com.allstore.api.producto.dto.ProductoUpdateRequest;
import com.allstore.api.producto.dto.VarianteResponse;
import com.allstore.api.producto.dto.VarianteUpdateRequest;
import com.allstore.api.producto.dto.VariantesRequest;
import com.allstore.api.producto.entity.Categoria;
import com.allstore.api.producto.entity.Color;
import com.allstore.api.producto.entity.Producto;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.entity.Talla;
import com.allstore.api.producto.repository.ColorRepository;
import com.allstore.api.producto.repository.ProductoRepository;
import com.allstore.api.producto.repository.ProductoVarianteRepository;
import com.allstore.api.producto.repository.TallaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final TallaRepository tallaRepository;
    private final ColorRepository colorRepository;
    private final CategoriaService categoriaService;

    @Transactional(readOnly = true)
    public PageResponse<ProductoResumenResponse> listar(String busqueda, Long categoriaId, Boolean activo,
                                                        Pageable pageable) {
        Specification<Producto> spec = Specification.unrestricted();
        if (StringUtils.hasText(busqueda)) {
            String patron = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(cb.lower(root.get("codigo")), patron)));
        }
        if (categoriaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId));
        }
        if (activo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        }
        return PageResponse.of(productoRepository.findAll(spec, pageable).map(ProductoResumenResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductoDetalleResponse obtenerDetalle(Long id) {
        return ProductoDetalleResponse.from(obtener(id));
    }

    @Transactional
    public ProductoDetalleResponse crear(ProductoCreateRequest request) {
        String codigo = request.codigo().trim().toUpperCase();
        if (productoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new BusinessException("Ya existe un producto con el código " + codigo);
        }
        boolean conTallas = request.tallaIds() != null && !request.tallaIds().isEmpty();
        boolean conColores = request.colorIds() != null && !request.colorIds().isEmpty();
        if (conTallas != conColores) {
            throw new BusinessException("Para generar variantes selecciona tallas y colores a la vez");
        }

        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(limpiar(request.descripcion()));
        producto.setCategoria(categoriaActiva(request.categoriaId()));
        producto.setPrecioVenta(request.precioVenta());

        List<ProductoVariante> nuevas = conTallas
                ? agregarCombinaciones(producto, request.tallaIds(), request.colorIds())
                : List.of();
        // Flush para que la base asigne el id de las variantes antes de derivar su código de barras.
        productoRepository.saveAndFlush(producto);
        asignarCodigosDeBarras(nuevas);
        return ProductoDetalleResponse.from(producto);
    }

    @Transactional
    public ProductoDetalleResponse actualizar(Long id, ProductoUpdateRequest request) {
        Producto producto = obtener(id);
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(limpiar(request.descripcion()));
        if (!producto.getCategoria().getId().equals(request.categoriaId())) {
            producto.setCategoria(categoriaActiva(request.categoriaId()));
        }
        producto.setPrecioVenta(request.precioVenta());
        producto.setActivo(request.activo());
        return ProductoDetalleResponse.from(producto);
    }

    @Transactional
    public ProductoDetalleResponse agregarVariantes(Long productoId, VariantesRequest request) {
        Producto producto = obtener(productoId);
        List<ProductoVariante> nuevas = agregarCombinaciones(producto, request.tallaIds(), request.colorIds());
        if (nuevas.isEmpty()) {
            throw new BusinessException("Todas las combinaciones seleccionadas ya existen en este producto");
        }
        // Se persisten las variantes directamente (no vía save del producto, que al ser una entidad
        // existente haría merge y devolvería copias, dejando sin id a las instancias de "nuevas").
        varianteRepository.saveAllAndFlush(nuevas);
        asignarCodigosDeBarras(nuevas);
        return ProductoDetalleResponse.from(producto);
    }

    @Transactional
    public VarianteResponse actualizarVariante(Long varianteId, VarianteUpdateRequest request) {
        ProductoVariante variante = varianteRepository.findById(varianteId)
                .orElseThrow(() -> new ResourceNotFoundException("Variante", varianteId));
        variante.setPrecioVenta(request.precioVenta());
        variante.setActivo(request.activo());
        return VarianteResponse.from(variante);
    }

    /** Para el punto de venta: busca por código de barras escaneado o por SKU. */
    @Transactional(readOnly = true)
    public VarianteResponse buscarVariantePorCodigo(String codigo) {
        return varianteRepository.findByCodigoBarrasOrSku(codigo.trim())
                .map(VarianteResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna prenda con el código " + codigo));
    }

    /** Crea en memoria las combinaciones talla x color que el producto aún no tiene. */
    private List<ProductoVariante> agregarCombinaciones(Producto producto, Collection<Long> tallaIds,
                                                        Collection<Long> colorIds) {
        List<Talla> tallas = cargarTallas(tallaIds);
        List<Color> colores = cargarColores(colorIds);

        List<ProductoVariante> nuevas = new ArrayList<>();
        for (Color color : colores) {
            for (Talla talla : tallas) {
                if (producto.tieneVariante(talla.getId(), color.getId())) {
                    continue;
                }
                ProductoVariante variante = new ProductoVariante();
                variante.setTalla(talla);
                variante.setColor(color);
                variante.setSku(CodigoVariante.sku(producto.getCodigo(), color.getCodigo(), talla.getCodigo()));
                producto.agregarVariante(variante);
                nuevas.add(variante);
            }
        }
        return nuevas;
    }

    private void asignarCodigosDeBarras(List<ProductoVariante> variantes) {
        variantes.forEach(v -> v.setCodigoBarras(CodigoVariante.ean13(v.getId())));
    }

    /** Tallas pedidas, validadas (existen y están activas) y en su orden lógico. */
    private List<Talla> cargarTallas(Collection<Long> ids) {
        Set<Long> unicos = new HashSet<>(ids);
        List<Talla> tallas = tallaRepository.findAllById(unicos);
        if (tallas.size() != unicos.size()) {
            throw new ResourceNotFoundException("Alguna talla seleccionada no existe");
        }
        tallas.stream().filter(t -> !t.isActivo()).findFirst().ifPresent(t -> {
            throw new BusinessException("La talla " + t.getCodigo() + " está inactiva y no se puede usar");
        });
        return tallas.stream().sorted(Comparator.comparing(Talla::getOrden)).toList();
    }

    /** Colores pedidos, validados (existen y están activos). */
    private List<Color> cargarColores(Collection<Long> ids) {
        Set<Long> unicos = new HashSet<>(ids);
        List<Color> colores = colorRepository.findAllById(unicos);
        if (colores.size() != unicos.size()) {
            throw new ResourceNotFoundException("Algún color seleccionado no existe");
        }
        colores.stream().filter(c -> !c.isActivo()).findFirst().ifPresent(c -> {
            throw new BusinessException("El color " + c.getNombre() + " está inactivo y no se puede usar");
        });
        return colores;
    }

    private Categoria categoriaActiva(Long categoriaId) {
        Categoria categoria = categoriaService.obtener(categoriaId);
        if (!categoria.isActivo()) {
            throw new BusinessException("La categoría " + categoria.getNombre() + " está inactiva");
        }
        return categoria;
    }

    private Producto obtener(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
