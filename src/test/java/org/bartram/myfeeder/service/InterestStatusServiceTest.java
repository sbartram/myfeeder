package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.bartram.myfeeder.integration.JevApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestStatusServiceTest {

    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;

    private CircuitBreakerRegistry registry;
    private InterestStatusService statusService;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        statusService = new InterestStatusService(jevApiClient, registry, interestService);
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
}
