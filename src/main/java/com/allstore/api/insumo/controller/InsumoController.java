package com.allstore.api.insumo.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.insumo.dto.AjusteInsumoRequest;
import com.allstore.api.insumo.dto.InsumoRequest;
import com.allstore.api.insumo.dto.InsumoResponse;
import com.allstore.api.insumo.dto.MovimientoInsumoResponse;
import com.allstore.api.insumo.dto.StockInsumoResponse;
import com.allstore.api.insumo.service.InsumoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Insumos de producción (tela, avíos). Consultar: cualquier usuario; registrar y ajustar: ADMIN y ALMACENERO. */
@RestController
@RequestMapping("/api/insumos")
@RequiredArgsConstructor
public class InsumoController {

    private final InsumoService insumoService;

    @GetMapping
    public List<InsumoResponse> listar() {
        return insumoService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InsumoResponse crear(@Valid @RequestBody InsumoRequest request) {
        return insumoService.crear(request);
    }

    @PutMapping("/{id}")
    public InsumoResponse actualizar(@PathVariable Long id, @Valid @RequestBody InsumoRequest request) {
        return insumoService.actualizar(id, request);
    }

    /** Saldo de cada insumo en cada local (o en uno). */
    @GetMapping("/stock")
    public List<StockInsumoResponse> stock(@RequestParam(required = false) Long establecimientoId) {
        return insumoService.stock(establecimientoId);
    }

    @GetMapping("/{id}/kardex")
    public PageResponse<MovimientoInsumoResponse> kardex(@PathVariable Long id, @RequestParam(required = false) Long establecimientoId,
                                                         @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return insumoService.kardex(id, establecimientoId, pageable);
    }

    /** Inventario inicial o ajuste de insumos en un local. */
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MovimientoInsumoResponse> ajustar(@Valid @RequestBody AjusteInsumoRequest request) {
        return insumoService.ajustar(request);
    }
}
