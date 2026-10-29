package com.allstore.api.entrega.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EntregaProperties.class, WhatsappProperties.class})
public class EntregaConfig {
}
