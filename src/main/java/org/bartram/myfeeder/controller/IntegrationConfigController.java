package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import org.bartram.myfeeder.integration.RaindropConfig;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationConfigController {

    private final IntegrationConfigRepository configRepository;
    private final JsonMapper objectMapper;

    @GetMapping
    public List<IntegrationConfig> listIntegrations() {
        return configRepository.findAll();
    }

    @PutMapping("/raindrop")
    public IntegrationConfig upsertRaindrop(@RequestBody RaindropConfig raindropConfig) {
        try {
            String configJson = objectMapper.writeValueAsString(raindropConfig);

            IntegrationConfig config = configRepository.findByType(IntegrationType.RAINDROP)
                    .orElseGet(() -> {
                        var c = new IntegrationConfig();
                        c.setType(IntegrationType.RAINDROP);
                        c.setEnabled(true);
                        return c;
                    });

            config.setConfig(configJson);
            return configRepository.save(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Raindrop config", e);
        }
    }

    @DeleteMapping("/raindrop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRaindrop() {
        configRepository.deleteByType(IntegrationType.RAINDROP);
    }
}
