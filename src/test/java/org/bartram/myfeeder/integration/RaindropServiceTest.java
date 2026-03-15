package org.bartram.myfeeder.integration;

import tools.jackson.databind.ObjectMapper;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock private IntegrationConfigRepository configRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private MyfeederProperties properties;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RaindropService raindropService;

    @Test
    void shouldThrowWhenConfigMissing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());

        var article = new Article();
        article.setTitle("Test");
        article.setUrl("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldThrowWhenConfigDisabled() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"apiToken\":\"tok\",\"collectionId\":123}");
        config.setEnabled(false);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        var article = new Article();
        article.setTitle("Test");
        article.setUrl("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
