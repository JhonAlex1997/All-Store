package com.allstore.api.promocion.controller;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.idempotencia.SolicitudIdempotente;
import com.allstore.api.common.idempotencia.UnaVez;
import com.allstore.api.promocion.dto.CampanaRequest;
import com.allstore.api.promocion.dto.CampanaResponse;
import com.allstore.api.promocion.dto.PrecioCampanaResponse;
import com.allstore.api.promocion.service.CampanaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Campañas de promoción: consultar, cualquier usuario (la caja ve los precios); crear y cambiar, ADMIN. */
@RestController
@RequestMapping("/api/campanas")
@RequiredArgsConstructor
public class CampanaController {

    static final int MAXIMO_PRENDAS = 200;

    private final UnaVez unaVez;
    private final CampanaService campanaService;

    /** Todas, o solo las vigentes hoy con ?vigentes=true. */
    @GetMapping
    public List<CampanaResponse> listar(@RequestParam(defaultValue = "false") boolean vigentes) {
        return campanaService.listar(vigentes);
    }

    @GetMapping("/{id}")
    public CampanaResponse obtener(@PathVariable Long id) {
        return campanaService.obtener(id);
    }

    /** Ej: GET /api/campanas/precios?varianteIds=15,16&establecimientoId=1: el precio de hoy con la campaña que aplica. */
    @GetMapping("/precios")
    public List<PrecioCampanaResponse> precios(@RequestParam List<Long> varianteIds, @RequestParam Long establecimientoId) {
        if (varianteIds.size() > MAXIMO_PRENDAS) {
            throw new BusinessException("Consulta como máximo " + MAXIMO_PRENDAS + " prendas a la vez");
        }
        return campanaService.precios(varianteIds, establecimientoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampanaResponse crear(@Valid @RequestBody CampanaRequest request,
        @RequestHeader(name = SolicitudIdempotente.CABECERA, required = false) String clave) {
        return unaVez.ejecutar(clave, "crear-campana", CampanaResponse.class,
                () -> campanaService.crear(request), request);
    }

    /** También para desactivarla ("activo": false) o cambiar sus fechas o alcance. */
    @PutMapping("/{id}")
    public CampanaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CampanaRequest request) {
        return campanaService.actualizar(id, request);
    }
}
