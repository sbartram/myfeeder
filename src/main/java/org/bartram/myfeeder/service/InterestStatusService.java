package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.JevApiClient;
import org.springframework.stereotype.Service;

/**
 * Assembles {@link InterestStatus} from its three live sources. Reading the breaker state does
 * not trip or reset the breaker, and the cold-start predicate is delegated (D-05), never
 * reimplemented here.
 */
@Service
@RequiredArgsConstructor
public class InterestStatusService {

    private final JevApiClient jevApiClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final InterestService interestService;

    public InterestStatus status() {
        return new InterestStatus(
                jevApiClient.isConfigured(),
                circuitBreakerRegistry.circuitBreaker("jev").getState().name(),
                interestService.isColdStart());
    }
}
