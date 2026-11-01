package com.allstore.api.inventario.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.inventario.dto.CerrarConteoRequest;
import com.allstore.api.inventario.dto.ConteoCantidadesRequest;
import com.allstore.api.inventario.dto.ConteoRequest;
import com.allstore.api.inventario.dto.ConteoResponse;
import com.allstore.api.inventario.entity.EstadoConteo;
import com.allstore.api.inventario.service.ConteoService;
import com.allstore.api.reporte.exportacion.RespuestaReporte;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conteo físico de inventario. Consultar: cualquier usuario; abrir, contar, cerrar y anular:
 * administrador y almacenero (ver SecurityConfig, /api/inventario/**).
 */
@RestController
@RequestMapping("/api/inventario/conteos")
@RequiredArgsConstructor
public class ConteoController {

    private final ConteoService conteoService;
    private final RespuestaReporte respuestaReporte;

    @GetMapping
    public PageResponse<ConteoResponse> listar(@RequestParam(required = false) Long establecimientoId,
                                               @RequestParam(required = false) EstadoConteo estado,
                                               @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return conteoService.listar(establecimientoId, estado, pageable);
    }

    /** Abre el conteo de un local (todo, una categoría o algunos modelos). Un solo conteo abierto por local. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConteoResponse abrir(@Valid @RequestBody ConteoRequest request) {
        return conteoService.abrir(request);
    }

    /** El conteo con sus prendas y diferencias. Con formato=XLSX o CSV, la hoja de conteo para imprimir. */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id,
                                     @RequestParam(defaultValue = "JSON") RespuestaReporte.Formato formato) {
        return formato == RespuestaReporte.Formato.JSON ? ResponseEntity.ok(conteoService.obtener(id))
                : respuestaReporte.responder(conteoService.hoja(id), formato);
    }

    /** Lo contado, por id o código escaneado. modo=SUMAR para escanear prenda por prenda. */
    @PutMapping("/{id}/cantidades")
    public ConteoResponse registrar(@PathVariable Long id, @Valid @RequestBody ConteoCantidadesRequest request) {
        return conteoService.registrar(id, request);
    }

    /** Ajusta las diferencias en el kardex y cierra el conteo. */
    @PostMapping("/{id}/cerrar")
    public ConteoResponse cerrar(@PathVariable Long id, @RequestBody(required = false) CerrarConteoRequest request) {
        return conteoService.cerrar(id, request);
    }

    /** Descarta el conteo sin ajustar nada. */
    @PostMapping("/{id}/anular")
    public ConteoResponse anular(@PathVariable Long id) {
        return conteoService.anular(id);
    }
}
