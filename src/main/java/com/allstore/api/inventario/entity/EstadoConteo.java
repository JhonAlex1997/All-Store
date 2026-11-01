package com.allstore.api.inventario.entity;

public enum EstadoConteo {
    /** Se está contando. */
    ABIERTO,
    /** Se cerró y sus diferencias quedaron ajustadas en el kardex. */
    CERRADO,
    /** Se descartó sin ajustar nada. */
    ANULADO
}
