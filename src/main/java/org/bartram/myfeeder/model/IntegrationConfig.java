package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("integration_config")
public class IntegrationConfig {
    @Id
    private Long id;
    private IntegrationType type;
    private String config;
    private boolean enabled;
}
