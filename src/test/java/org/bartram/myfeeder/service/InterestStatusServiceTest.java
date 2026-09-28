package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoreCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestStatusServiceTest {

    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;
    @Mock private ArticleScoreStore store;

    private CircuitBreakerRegistry registry;
    private InterestStatusService statusService;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        statusService = new InterestStatusService(jevApiClient, registry, interestService, store,
                new MyfeederProperties());
        lenient().when(store.counts(any())).thenReturn(new ScoreCounts(0, 0));
    }

    @Test
    void reportsConfiguredAndColdStartFromTheirSources() {
        when(jevApiClient.isConfigured()).thenReturn(true);
        when(interestService.isColdStart()).thenReturn(false);

        InterestStatus status = statusService.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.coldStart()).isFalse();
        assertThat(status.breakerState()).isEqualTo("CLOSED");
    }

    @Test
    void passesBreakerStateNamesThrough() {
        CircuitBreaker jev = registry.circuitBreaker("jev");

        jev.transitionToOpenState();
        assertThat(statusService.status().breakerState()).isEqualTo("OPEN");

        jev.transitionToForcedOpenState();
        assertThat(statusService.status().breakerState()).isEqualTo("FORCED_OPEN");

        jev.reset();
        jev.transitionToOpenState();
        jev.transitionToHalfOpenState();
        assertThat(statusService.status().breakerState()).isEqualTo("HALF_OPEN");
    }

    @Test
    void delegatesColdStartToInterestService() {
        when(interestService.isColdStart()).thenReturn(true);

        assertThat(statusService.status().coldStart()).isTrue();
        verify(interestService, times(1)).isColdStart();

        statusService.status();
        verify(interestService, times(2)).isColdStart();
    }

    @Test
    void reportsEligibleUnscoredAndFailedCounts() {
        when(store.counts(any())).thenReturn(new ScoreCounts(312, 4));

        InterestStatus status = statusService.status();

        assertThat(status.eligibleUnscored()).isEqualTo(312);
        assertThat(status.failed()).isEqualTo(4);
    }

    @Test
    void countsUseTheEligibilityWindow() {
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        statusService.status();

        verify(store).counts(cutoff.capture());
        Instant expected = Instant.now().minus(Duration.ofDays(14));
        assertThat(cutoff.getValue()).isCloseTo(expected, within(Duration.ofSeconds(5)));
    }

    @Test
    void servesTheDefaultTierThresholds() {
        assertThat(statusService.status().tiers()).isEqualTo(new TierThresholds(70, 40));
    }

    @Test
    void tiersBindFromTheBlendTiersKeys() {
        MyfeederProperties bound = new Binder(new MapConfigurationPropertySource(Map.of(
                "myfeeder.interest.blend.tiers.high", "55",
                "myfeeder.interest.blend.tiers.neutral", "25")))
                .bind("myfeeder", MyfeederProperties.class).get();
        InterestStatusService tuned = new InterestStatusService(jevApiClient, registry, interestService, store, bound);

        assertThat(tuned.status().tiers()).isEqualTo(new TierThresholds(55, 25));
        assertThat(bound.getInterest().getBlend().getProfilePoints()).isEqualTo(100);
    }
}
