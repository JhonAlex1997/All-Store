package com.allstore.api.venta.service;

/**
 * Se pidió entregar al cliente (por WhatsApp o correo) un comprobante que no pasa por SUNAT (nota
 * de venta): se envía en el acto. Las boletas y facturas esperan a que SUNAT las acepte.
 */
public record EntregaSolicitada(Long comprobanteId) {
}
