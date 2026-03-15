package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface IntegrationConfigRepository extends ListCrudRepository<IntegrationConfig, Long> {

    Optional<IntegrationConfig> findByType(IntegrationType type);

    void deleteByType(IntegrationType type);
}
