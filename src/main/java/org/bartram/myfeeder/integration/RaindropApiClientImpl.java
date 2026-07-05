package org.bartram.myfeeder.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class RaindropApiClientImpl implements RaindropApiClient {

    private final String apiToken;
    private final RestClient restClient;

    public RaindropApiClientImpl(MyfeederProperties properties, RestClient.Builder builder) {
        this.apiToken = properties.getRaindrop().getApiToken();
        this.restClient = builder
                .baseUrl(properties.getRaindrop().getApiBaseUrl())
                .build();
    }

    private void requireConfigured() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new RaindropNotConfiguredException();
        }
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "listCollectionsFallback")
    @Retry(name = "raindrop")
    @Override
    public List<RaindropCollection> listCollections() {
        requireConfigured();
        CollectionsResponse response = restClient
                .get()
                .uri("/collections")
                .header("Authorization", "Bearer " + apiToken)
                .retrieve()
                .body(CollectionsResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(item -> new RaindropCollection(item.id(), item.title()))
                .toList();
    }

    @CircuitBreaker(name = "raindrop", fallbackMethod = "createBookmarkFallback")
    @Retry(name = "raindrop")
    @Override
    public void createBookmark(Long collectionId, String url, String title) {
        requireConfigured();
        var body = new CreateRaindropRequest(url, title, new CollectionRef(collectionId));
        restClient
                .post()
                .uri("/raindrop")
                .header("Authorization", "Bearer " + apiToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private List<RaindropCollection> listCollectionsFallback(Throwable throwable) {
        // rethrow as-is: resilience4j ignore-exceptions (application.yaml) matches on this exact type
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }

    @SuppressWarnings("unused")
    private void createBookmarkFallback(Long collectionId, String url, String title, Throwable throwable) {
        // rethrow as-is: resilience4j ignore-exceptions (application.yaml) matches on this exact type
        if (throwable instanceof RaindropNotConfiguredException rnc) {
            throw rnc;
        }
        throw new IllegalStateException("Raindrop.io is currently unavailable", throwable);
    }

    private record CollectionsResponse(List<CollectionItem> items) {}

    private record CollectionItem(
            @com.fasterxml.jackson.annotation.JsonProperty("_id") Long id,
            String title) {}

    private record CreateRaindropRequest(String link, String title, CollectionRef collection) {}

    private record CollectionRef(@com.fasterxml.jackson.annotation.JsonProperty("$id") Long id) {}
}
