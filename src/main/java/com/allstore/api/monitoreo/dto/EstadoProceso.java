package com.allstore.api.monitoreo.dto;

import java.time.OffsetDateTime;

/**
 * Un proceso programado.
 *
 * @param ultimoInicio cuándo empezó su última vuelta; null si no corrió desde el arranque
 * @param ultimoFin    cuándo terminó su última vuelta
 * @param detenido     no termina una vuelta hace más de lo esperado: dejó de enviar
 * @param fallos       vueltas que fallaron desde el arranque
 * @param ultimoError  el error de la última vuelta; null si terminó bien
 */
public record EstadoProceso(String proceso, OffsetDateTime ultimoInicio, OffsetDateTime ultimoFin,
                            boolean detenido, long fallos, String ultimoError) {
}
