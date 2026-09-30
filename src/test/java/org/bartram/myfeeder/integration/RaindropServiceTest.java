package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.EngagementKind;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.ArticleEngagementStore;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock private IntegrationConfigRepository configRepository;
    @Mock private RaindropApiClient raindropApiClient;
    @Mock private ArticleEngagementStore engagementStore;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RaindropService raindropService;

    @Test
    void shouldThrowWhenConfigMissing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());

        var article = articleAt("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
        verifyNoInteractions(engagementStore);
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
        verifyNoInteractions(engagementStore);
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
        verifyNoInteractions(engagementStore);
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

        InOrder inOrder = inOrder(raindropApiClient, engagementStore);
        inOrder.verify(raindropApiClient).createBookmark(456L, "https://example.com/x", "X");
        inOrder.verify(engagementStore).recordQuietly(42L, EngagementKind.RAINDROP);
    }

    @Test
    void aFailedOrBlockedBookmarkRecordsNothing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(enabledConfig()));
        // The fallback's shape for a failed call or an open breaker
        doThrow(new IllegalStateException("Raindrop.io is currently unavailable"))
                .when(raindropApiClient).createBookmark(anyLong(), anyString(), anyString());

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
        verifyNoInteractions(engagementStore);
    }

    @Test
    void aRaindropNotConfiguredFailureRecordsNothing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(enabledConfig()));
        doThrow(new RaindropNotConfiguredException())
                .when(raindropApiClient).createBookmark(anyLong(), anyString(), anyString());

        assertThatThrownBy(() -> raindropService.saveToRaindrop(articleAt("https://example.com")))
                .isInstanceOf(RaindropNotConfiguredException.class);
        verifyNoInteractions(engagementStore);
    }

    @Test
    void aRepeatedSaveCapturesEachTimeQuietly() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(enabledConfig()));
        var article = articleAt("https://example.com/x");

        raindropService.saveToRaindrop(article);
        raindropService.saveToRaindrop(article);

        // The store's ON CONFLICT DO NOTHING keeps one row (ArticleEngagementStoreTest.recordIsIdempotent)
        verify(raindropApiClient, times(2)).createBookmark(456L, "https://example.com/x", "t");
        verify(engagementStore, times(2)).recordQuietly(42L, EngagementKind.RAINDROP);
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

    private static IntegrationConfig enabledConfig() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"collectionId\":456}");
        config.setEnabled(true);
        return config;
    }

    private static Article articleAt(String url) {
        var a = new Article();
        a.setId(42L);
        a.setUrl(url);
        a.setTitle("t");
        return a;
    }
}
