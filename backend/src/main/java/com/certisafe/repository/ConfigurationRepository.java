package com.certisafe.repository;

import com.certisafe.domain.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    Optional<Configuration> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
