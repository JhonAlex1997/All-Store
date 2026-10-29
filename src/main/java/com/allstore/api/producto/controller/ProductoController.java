package com.allstore.api.producto.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.producto.dto.EtiquetasRequest;
import com.allstore.api.producto.dto.ProductoCreateRequest;
import com.allstore.api.producto.dto.ProductoDetalleResponse;
import com.allstore.api.producto.dto.ProductoResumenResponse;
import com.allstore.api.producto.dto.ProductoUpdateRequest;
import com.allstore.api.producto.dto.VarianteResponse;
import com.allstore.api.producto.dto.VarianteUpdateRequest;
import com.allstore.api.producto.dto.VariantesRequest;
import com.allstore.api.producto.service.EtiquetaService;
import com.allstore.api.producto.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final EtiquetaService etiquetaService;

    /** Ej: GET /api/productos?busqueda=mom&categoriaId=1&page=0&size=20 */
    @GetMapping
    public PageResponse<ProductoResumenResponse> listar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean activo,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return productoService.listar(busqueda, categoriaId, activo, pageable);
    }

    @GetMapping("/{id}")
    public ProductoDetalleResponse obtener(@PathVariable Long id) {
        return productoService.obtenerDetalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoDetalleResponse crear(@Valid @RequestBody ProductoCreateRequest request) {
        return productoService.crear(request);
    }

    @PutMapping("/{id}")
    public ProductoDetalleResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoUpdateRequest request) {
        return productoService.actualizar(id, request);
    }

    @PostMapping("/{id}/variantes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoDetalleResponse agregarVariantes(@PathVariable Long id, @Valid @RequestBody VariantesRequest request) {
        return productoService.agregarVariantes(id, request);
    }

    @PatchMapping("/variantes/{varianteId}")
    public VarianteResponse actualizarVariante(@PathVariable Long varianteId,
                                               @Valid @RequestBody VarianteUpdateRequest request) {
        return productoService.actualizarVariante(varianteId, request);
    }

    /** Para el punto de venta: código de barras escaneado o SKU. */
    @GetMapping("/variantes/codigo/{codigo}")
    public VarianteResponse buscarVariante(@PathVariable String codigo) {
        return productoService.buscarVariantePorCodigo(codigo);
    }

    /**
     * PDF de etiquetas con código de barras para pegar en las prendas. Ej.:
     * {"formato": "ROLLO_50X25", "items": [{"varianteId": 15, "cantidad": 6}]}
     */
    @PostMapping("/etiquetas")
    public ResponseEntity<byte[]> etiquetas(@Valid @RequestBody EtiquetasRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("etiquetas.pdf").build().toString())
                .body(etiquetaService.generar(request));
    }
}
