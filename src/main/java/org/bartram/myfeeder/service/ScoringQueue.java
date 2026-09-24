package org.bartram.myfeeder.service;

import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.InterestScoringConfig;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands article ids to the dedicated scoring executor. An id that is already queued or running is
 * not submitted again, and it is released when scoring ends (even if the scorer throws) or when the
 * executor rejects it, so the sweep can always re-enqueue it. Never blocks or throws on a full queue.
 * Logs carry counts, ids and exception class names only.
 */
@Slf4j
@Component
public class ScoringQueue {

    private final TaskExecutor executor;
    private final ArticleScoringService scorer;
    private final ArticleScoreStore store;
    private final MyfeederProperties properties;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    // Explicit constructor: there is no lombok.config, so a Lombok constructor would drop the @Qualifier
    public ScoringQueue(@Qualifier(InterestScoringConfig.EXECUTOR) TaskExecutor executor,
                        ArticleScoringService scorer,
                        ArticleScoreStore store,
                        MyfeederProperties properties) {
        this.executor = executor;
        this.scorer = scorer;
        this.store = store;
        this.properties = properties;
    }

    /** Filters freshly ingested ids through the shared eligibility predicate, then submits them newest first. */
    public void submitIngested(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        submit(store.filterNeedingScoring(ids, properties.getInterest().eligibilityCutoff()));
    }

    /** Submits ids in the given order and returns how many were newly accepted. */
    public int submit(List<Long> ids) {
        int accepted = 0;
        int dropped = 0;
        for (Long id : ids) {
            if (!inFlight.add(id)) {
                continue; // already queued or running
            }
            try {
                executor.execute(() -> run(id));
                accepted++;
            } catch (TaskRejectedException e) {
                inFlight.remove(id); // otherwise the sweep could never re-enqueue it
                dropped++;
            }
        }
        if (dropped > 0) {
            log.warn("Scoring queue full; dropped {} articles (the sweep will retry them)", dropped);
        }
        return accepted;
    }

    private void run(long id) {
        try {
            scorer.score(id);
        } catch (RuntimeException e) {
            log.warn("Scoring article {} failed unexpectedly: {}", id, e.getClass().getSimpleName());
        } finally {
            inFlight.remove(id);
        }
    }

    /** Free queue slots; unbounded for an inline executor. */
    public int remainingCapacity() {
        if (executor instanceof ThreadPoolTaskExecutor tp) {
            return tp.getQueueCapacity() - tp.getQueueSize();
        }
        return Integer.MAX_VALUE;
    }

    boolean isInFlight(long id) {
        return inFlight.contains(id);
    }
}
