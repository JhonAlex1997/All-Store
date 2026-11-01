package com.allstore.api.produccion.controller;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.produccion.dto.AnularOrdenRequest;
import com.allstore.api.produccion.dto.ConsumoRequest;
import com.allstore.api.produccion.dto.EtapaRequest;
import com.allstore.api.produccion.dto.OrdenRequest;
import com.allstore.api.produccion.dto.OrdenResponse;
import com.allstore.api.produccion.dto.TerminarOrdenRequest;
import com.allstore.api.produccion.entity.EstadoOrden;
import com.allstore.api.produccion.entity.Etapa;
import com.allstore.api.produccion.service.ProduccionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Producción (ADMIN y ALMACENERO, ver SecurityConfig). */
@RestController
@RequestMapping("/api/produccion/ordenes")
@RequiredArgsConstructor
public class ProduccionController {

    private final UnaVez unaVez;
    private final ProduccionService produccionService;

    @GetMapping
    public PageResponse<OrdenResponse> listar(@RequestParam(required = false) EstadoOrden estado,
                                              @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return produccionService.listar(estado, pageable);
    }

    @GetMapping("/{id}")
    public OrdenResponse obtener(@PathVariable Long id) {
        return produccionService.obtener(id);
    }

    /** Nueva orden de un modelo con las tallas y colores a producir. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse crear(@Valid @RequestBody OrdenRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "crear-orden", OrdenResponse.class,
                () -> produccionService.crear(request), request);
    }

    /** Consume insumos (tela, avíos) del local de insumos. */
    @PostMapping("/{id}/insumos")
    public OrdenResponse consumir(@PathVariable Long id, @Valid @RequestBody ConsumoRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "consumir-insumos", OrdenResponse.class,
                () -> produccionService.consumir(id, request), id, request);
    }

    /** Devuelve insumos sobrantes al local de insumos. */
    @PostMapping("/{id}/insumos/devolver")
    public OrdenResponse devolver(@PathVariable Long id, @Valid @RequestBody ConsumoRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "devolver-insumos", OrdenResponse.class,
                () -> produccionService.devolver(id, request), id, request);
    }

    /** Inicia una etapa (CORTE, CONFECCION, LAVADO, ACABADO), opcionalmente en un taller externo. */
    @PostMapping("/{id}/etapas/{etapa}/iniciar")
    public OrdenResponse iniciarEtapa(@PathVariable Long id, @PathVariable Etapa etapa,
                                      @Valid @RequestBody(required = false) EtapaRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "iniciar-etapa", OrdenResponse.class,
                () -> produccionService.iniciarEtapa(id, etapa, request), id, etapa, request);
    }

    /** Termina una etapa con las prendas que salieron buenas y lo que cobró el taller. */
    @PostMapping("/{id}/etapas/{etapa}/terminar")
    public OrdenResponse terminarEtapa(@PathVariable Long id, @PathVariable Etapa etapa, @Valid @RequestBody EtapaRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "terminar-etapa", OrdenResponse.class,
                () -> produccionService.terminarEtapa(id, etapa, request), id, etapa, request);
    }

    /** Cierra la orden: las prendas entran al stock con su costo calculado. */
    @PostMapping("/{id}/terminar")
    public OrdenResponse terminar(@PathVariable Long id, @Valid @RequestBody TerminarOrdenRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "terminar-orden", OrdenResponse.class,
                () -> produccionService.terminar(id, request), id, request);
    }

    @PostMapping("/{id}/anular")
    public OrdenResponse anular(@PathVariable Long id, @Valid @RequestBody AnularOrdenRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "anular-orden", OrdenResponse.class,
                () -> produccionService.anular(id, request), id, request);
    }
}
