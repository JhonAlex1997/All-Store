package com.allstore.api.facturacion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa la configuración de SUNAT y el proceso programado que envía los pendientes. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SunatProperties.class)
public class SunatConfig {
}
