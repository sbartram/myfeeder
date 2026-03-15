package org.bartram.myfeeder.integration;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaindropService {

    private final IntegrationConfigRepository configRepository;
    private final RestClient.Builder restClientBuilder;
    private final MyfeederProperties properties;
    private final ObjectMapper objectMapper;

    public void saveToRaindrop(Article article) {
        var integrationConfig = configRepository.findByType(IntegrationType.RAINDROP)
                .orElseThrow(() -> new IllegalStateException("Raindrop.io is not configured"));

        if (!integrationConfig.isEnabled()) {
            throw new IllegalStateException("Raindrop.io integration is disabled");
        }

        RaindropConfig config;
        try {
            config = objectMapper.readValue(integrationConfig.getConfig(), RaindropConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Raindrop configuration", e);
        }

        var body = Map.of(
                "link", article.getUrl(),
                "title", article.getTitle(),
                "collection", Map.of("$id", config.getCollectionId())
        );

        restClientBuilder.build()
                .post()
                .uri(properties.getRaindrop().getApiBaseUrl() + "/raindrop")
                .header("Authorization", "Bearer " + config.getApiToken())
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Saved article '{}' to Raindrop.io", article.getTitle());
    }
}
