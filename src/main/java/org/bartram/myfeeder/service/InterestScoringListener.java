package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.event.ArticlesIngestedEvent;
import org.bartram.myfeeder.integration.JevApiClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands newly ingested articles to the scoring queue.
 *
 * <p>{@code pollFeed} has no transaction, so this listener runs inline on the single polling
 * thread. It therefore only enqueues (the scorer runs on the jev-score executor) and never throws:
 * an exception here must not reach the poll's error bookkeeping (SCOR-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestScoringListener {

    private final JevApiClient jevApiClient;
    private final ScoringQueue scoringQueue;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onArticlesIngested(ArticlesIngestedEvent event) {
        try {
            if (!jevApiClient.isConfigured()) {
                return;
            }
            scoringQueue.submitIngested(event.articleIds());
        } catch (RuntimeException e) {
            log.warn("Could not enqueue {} new articles for scoring: {}",
                    event.articleIds().size(), e.getClass().getSimpleName());
        }
    }
}
