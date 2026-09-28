package org.bartram.myfeeder.scheduler;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.service.InterestService;
import org.bartram.myfeeder.service.ScoringQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only drain loop for interest scoring. Because "needs scoring" lives in the database, this one
 * job covers the launch backfill, recovery after an outage, a key added later, the first scoring
 * after the profile is written and the Re-score drain (research R3, D-10).
 *
 * <p>It runs on the shared scheduler thread, so it only selects ids and enqueues them; the Jev call
 * happens on the {@code jev-score-} executor. Each run enqueues at most
 * {@code min(free queue room, sweep-batch-size)} ids, newest first (D-16), so fresh arrivals never
 * wait behind a large backlog. It skips while Jev is unconfigured, in cold start (SCOR-06), or while
 * the jev breaker is OPEN or FORCED_OPEN; HALF_OPEN runs, so the automatic OPEN to HALF_OPEN
 * transition (D-17) resumes scoring on its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestScoringSweep {

    private final JevApiClient jevApiClient;
    private final InterestService interestService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ScoringQueue scoringQueue;
    private final ArticleScoreStore store;
    private final MyfeederProperties properties;

    @Scheduled(fixedDelayString = "${myfeeder.interest.sweep-delay}",
            initialDelayString = "${myfeeder.interest.sweep-initial-delay}")
    public void sweep() {
        try {
            if (!jevApiClient.isConfigured()) {
                return;
            }
            CircuitBreaker.State state = circuitBreakerRegistry.circuitBreaker("jev").getState();
            if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                return;
            }
            if (interestService.isColdStart()) {
                return;
            }
            int room = scoringQueue.remainingCapacity();
            if (room <= 0) {
                return;
            }
            MyfeederProperties.Interest interest = properties.getInterest();
            List<Long> ids = store.findNeedingScoring(interest.eligibilityCutoff(),
                    Math.min(room, interest.getSweepBatchSize()));
            if (!ids.isEmpty()) {
                int accepted = scoringQueue.submit(ids);
                log.debug("Scoring sweep enqueued {} of {} articles", accepted, ids.size());
            }
        } catch (RuntimeException e) {
            log.warn("Scoring sweep failed: {}", e.getClass().getSimpleName());
        }
    }
}
