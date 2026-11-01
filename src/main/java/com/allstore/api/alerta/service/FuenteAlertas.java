package com.allstore.api.alerta.service;

import com.allstore.api.alerta.dto.Alerta;

import java.util.List;

/**
 * Otro módulo que aporta alertas al panel y al resumen por correo (sistema, stock...). Basta con
 * implementarla como componente: AlertaService las reúne todas.
 */
public interface FuenteAlertas {

    List<Alerta> alertas();
}
