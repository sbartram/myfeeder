package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Re-score (INT-05). The count and the reset share {@link ArticleScoreStore}'s RESCORE_SCOPE, so the
 * confirmation shows exactly what is reset (D-02). The scope is in-window unread articles with a
 * SCORED or FAILED row (exhausted FAILED included); SKIPPED rows are never touched (D-03).
 *
 * <p>After a reset the backfill sweep re-scores the rows; there is no immediate kick.
 */
@Service
@RequiredArgsConstructor
public class InterestRescoreService {

    private final JevApiClient jevApiClient;
    private final InterestService interestService;
    private final ArticleScoreStore store;
    private final MyfeederProperties properties;

    /** How many score rows Re-score would reset. Not guarded: counting deletes nothing. */
    public RescoreCount count() {
        return new RescoreCount(store.countRescoreScope(cutoff()), windowDays());
    }

    /**
     * Deletes the Re-score scope and returns the rows deleted. Refuses with a fixed-text
     * {@link IllegalStateException} (409 via GlobalExceptionHandler) when nothing could re-score the
     * rows: no TypeSafe key, or cold start (D-18). The delete is one statement, so no transaction is
     * needed.
     */
    public RescoreCount rescore() {
        if (!jevApiClient.isConfigured() || interestService.isColdStart()) {
            throw new IllegalStateException("Re-score needs a TypeSafe API key and a profile or at least one topic");
        }
        return new RescoreCount(store.deleteRescoreScope(cutoff()), windowDays());
    }

    private Instant cutoff() {
        return properties.getInterest().eligibilityCutoff();
    }

    private int windowDays() {
        return properties.getInterest().getWindowDays();
    }
}
