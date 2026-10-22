package com.certisafe.config;

import com.certisafe.domain.Configuration;
import com.certisafe.repository.ConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura de los parametros del sistema almacenados en la tabla {@code configurations}.
 *
 * <p>Los valores NO se duplican en {@code application.yml}: la fuente de verdad es la base
 * de datos (asi el ADMIN los puede cambiar sin reiniciar). El valor por defecto del codigo
 * solo actua como red de seguridad si la fila no existe o esta corrupta.</p>
 */
@Service
@Transactional(readOnly = true)
public class ParameterService {

    private static final Logger log = LoggerFactory.getLogger(ParameterService.class);

    private final ConfigurationRepository configurationRepository;

    public ParameterService(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    public int getInt(String key, int fallback) {
        return configurationRepository.findByConfigKey(key)
                .map(Configuration::getValue)
                .map(String::trim)
                .map(value -> {
                    try {
                        return Integer.valueOf(value);
                    } catch (NumberFormatException ex) {
                        log.warn("El parametro '{}' no es un entero valido (valor='{}'). Se usa {}.",
                                key, value, fallback);
                        return Integer.valueOf(fallback);
                    }
                })
                .orElseGet(() -> {
                    log.warn("El parametro '{}' no existe en configurations. Se usa {}.", key, fallback);
                    return Integer.valueOf(fallback);
                });
    }
}
