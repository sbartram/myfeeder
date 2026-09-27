package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoreCounts;
import org.springframework.stereotype.Service;

/**
 * Assembles {@link InterestStatus} from its live sources. Reading the breaker state does
 * not trip or reset the breaker, and the cold-start predicate is delegated (D-05), never
 * reimplemented here. Both counts come from one {@link ArticleScoreStore#counts} call.
 */
@Service
@RequiredArgsConstructor
public class InterestStatusService {

    private final JevApiClient jevApiClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final InterestService interestService;
    private final ArticleScoreStore store;
    private final MyfeederProperties properties;

    public InterestStatus status() {
        ScoreCounts c = store.counts(properties.getInterest().eligibilityCutoff());
        MyfeederProperties.Interest.Blend.Tiers t = properties.getInterest().getBlend().getTiers();
        return new InterestStatus(
                jevApiClient.isConfigured(),
                circuitBreakerRegistry.circuitBreaker("jev").getState().name(),
                interestService.isColdStart(),
                c.eligibleUnscored(),
                c.failed(),
                new TierThresholds(t.getHigh(), t.getNeutral()));
    }
}
