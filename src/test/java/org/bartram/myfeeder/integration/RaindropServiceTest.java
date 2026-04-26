package org.bartram.myfeeder.integration;

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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock private IntegrationConfigRepository configRepository;
    @Mock private RaindropApiClient raindropApiClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RaindropService raindropService;

    @Test
    void shouldThrowWhenConfigMissing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());

        var article = articleAt("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldThrowWhenConfigDisabled() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"collectionId\":123}");
        config.setEnabled(false);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldThrowWhenNoCollectionSelected() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{}");
        config.setEnabled(true);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collection");
    }

    @Test
    void shouldDelegateToClientWhenConfigured() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"collectionId\":456}");
        config.setEnabled(true);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        var article = articleAt("https://example.com/x");
        article.setTitle("X");

        raindropService.saveToRaindrop(article);

        verify(raindropApiClient).createBookmark(456L, "https://example.com/x", "X");
    }

    @Test
    void listCollectionsSortsByTitleCaseInsensitive() {
        when(raindropApiClient.listCollections()).thenReturn(List.of(
                new RaindropCollection(3L, "zebra"),
                new RaindropCollection(1L, "Apple"),
                new RaindropCollection(2L, "banana")));

        List<RaindropCollection> result = raindropService.listCollections();

        assertThat(result).extracting(RaindropCollection::title)
                .containsExactly("Apple", "banana", "zebra");
    }

    private static Article articleAt(String url) {
        var a = new Article();
        a.setUrl(url);
        a.setTitle("t");
        return a;
    }
}
