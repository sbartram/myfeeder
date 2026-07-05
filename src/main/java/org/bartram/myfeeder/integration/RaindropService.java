package org.bartram.myfeeder.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaindropService {

    private final IntegrationConfigRepository configRepository;
    private final RaindropApiClient raindropApiClient;
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

        if (config.getCollectionId() == null) {
            throw new IllegalArgumentException("No Raindrop collection selected. Pick one in Settings.");
        }

        raindropApiClient.createBookmark(config.getCollectionId(), article.getUrl(), article.getTitle());
        log.info("Saved article '{}' to Raindrop.io", article.getTitle());
    }

    @Cacheable(value = "raindrop-collections", unless = "#result == null || #result.isEmpty()")
    public List<RaindropCollection> listCollections() {
        return raindropApiClient.listCollections().stream()
                .sorted(Comparator.comparing(RaindropCollection::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
