package com.allstore.api.caja.controller;

import com.allstore.api.caja.dto.AbrirCajaRequest;
import com.allstore.api.caja.dto.CajaResponse;
import com.allstore.api.caja.dto.CajaSesionResumenResponse;
import com.allstore.api.caja.dto.CerrarCajaRequest;
import com.allstore.api.caja.dto.MovimientoCajaRequest;
import com.allstore.api.caja.entity.EstadoCaja;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/caja")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService cajaService;

    @PostMapping("/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponse abrir(@Valid @RequestBody AbrirCajaRequest request) {
        return cajaService.abrir(request);
    }

    /** Mi caja abierta con el cuadre al momento. */
    @GetMapping("/actual")
    public CajaResponse miCaja() {
        return cajaService.miCaja();
    }

    /** Ingreso o egreso de efectivo que no es venta (ej. pago de un taxi). */
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponse registrarMovimiento(@Valid @RequestBody MovimientoCajaRequest request) {
        return cajaService.registrarMovimiento(request);
    }

    @PostMapping("/cerrar")
    public CajaResponse cerrar(@Valid @RequestBody CerrarCajaRequest request) {
        return cajaService.cerrar(request);
    }

    /** Historial de cajas (solo ADMIN, ver SecurityConfig). */
    @GetMapping("/sesiones")
    public PageResponse<CajaSesionResumenResponse> listar(
            @RequestParam(required = false) Long establecimientoId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) EstadoCaja estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaApertura", direction = Sort.Direction.DESC) Pageable pageable) {
        return cajaService.listar(establecimientoId, usuario, estado, desde, hasta, pageable);
    }

    @GetMapping("/sesiones/{id}")
    public CajaResponse obtener(@PathVariable Long id) {
        return cajaService.obtener(id);
    }
}
