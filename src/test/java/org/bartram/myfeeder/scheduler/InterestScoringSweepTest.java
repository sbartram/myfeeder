package org.bartram.myfeeder.scheduler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.service.InterestService;
import org.bartram.myfeeder.service.ScoringQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestScoringSweepTest {

    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;
    @Mock private ScoringQueue scoringQueue;
    @Mock private ArticleScoreStore store;

    private CircuitBreakerRegistry registry;
    private InterestScoringSweep sweep;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        sweep = new InterestScoringSweep(jevApiClient, interestService, registry, scoringQueue, store,
                new MyfeederProperties());
    }

    /** Every gate open: configured, rubric present, room in the queue. */
    private void gatesOpen(int room) {
        lenient().when(jevApiClient.isConfigured()).thenReturn(true);
        lenient().when(interestService.isColdStart()).thenReturn(false);
        lenient().when(scoringQueue.remainingCapacity()).thenReturn(room);
    }

    private void assertNothingSelectedOrSubmitted() {
        verifyNoInteractions(store);
        verify(scoringQueue, never()).submit(anyList());
    }

    @Test
    void skipsWhenJevIsNotConfigured() {
        gatesOpen(1000);
        when(jevApiClient.isConfigured()).thenReturn(false);

        sweep.sweep();

        assertNothingSelectedOrSubmitted();
    }

    @Test
    void skipsInColdStart() {
        gatesOpen(1000);
        when(interestService.isColdStart()).thenReturn(true);

        sweep.sweep();

        assertNothingSelectedOrSubmitted();
    }

    @Test
    void skipsWhileTheBreakerIsOpenOrForcedOpen() {
        gatesOpen(1000);
        CircuitBreaker jev = registry.circuitBreaker("jev");

        jev.transitionToOpenState();
        sweep.sweep();
        assertNothingSelectedOrSubmitted();

        jev.transitionToForcedOpenState();
        sweep.sweep();
        assertNothingSelectedOrSubmitted();
    }

    @Test
    void runsWhenTheBreakerIsHalfOpen() {
        gatesOpen(1000);
        CircuitBreaker jev = registry.circuitBreaker("jev");
        jev.transitionToOpenState();
        jev.transitionToHalfOpenState();
        when(store.findNeedingScoring(any(Instant.class), anyInt())).thenReturn(List.of(3L, 2L));

        sweep.sweep();

        verify(store).findNeedingScoring(any(Instant.class), eq(50));
        verify(scoringQueue).submit(List.of(3L, 2L));
    }

    @Test
    void enqueuesAtMostTheBatchCapNewestFirst() {
        gatesOpen(1000);
        List<Long> newestFirst = List.of(42L, 17L, 9L);
        when(store.findNeedingScoring(any(Instant.class), anyInt())).thenReturn(newestFirst);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<List<Long>> submitted = ArgumentCaptor.captor();

        sweep.sweep();

        verify(store).findNeedingScoring(cutoff.capture(), eq(50));
        assertThat(cutoff.getValue())
                .isCloseTo(Instant.now().minus(Duration.ofDays(14)), within(Duration.ofSeconds(5)));
        verify(scoringQueue).submit(submitted.capture());
        assertThat(submitted.getValue()).containsExactly(42L, 17L, 9L);
    }

    @Test
    void enqueuesAtMostTheFreeRoom() {
        gatesOpen(7);
        when(store.findNeedingScoring(any(Instant.class), anyInt())).thenReturn(List.of(1L));

        sweep.sweep();

        verify(store).findNeedingScoring(any(Instant.class), eq(7));
        verify(scoringQueue).submit(List.of(1L));
    }

    @Test
    void fullQueueRunsNoQuery() {
        gatesOpen(0);

        sweep.sweep();

        assertNothingSelectedOrSubmitted();
    }

    @Test
    void failuresAreContained() {
        gatesOpen(1000);
        when(store.findNeedingScoring(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("database down"));

        assertThatCode(() -> sweep.sweep()).doesNotThrowAnyException();
        verify(scoringQueue, never()).submit(anyList());
    }
}
