package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.InterestScoringConfig;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.event.ArticlesIngestedEvent;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Success criterion 2 (SCOR-02): scoring runs on the dedicated jev-score executor and nothing it
 * does can slow or fail a poll. Uses the real executor, queue and listener; only the scorer, the
 * store and the Jev client are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ScoringIsolationTest {

    @Mock private ArticleScoringService scorer;
    @Mock private ArticleScoreStore store;
    @Mock private JevApiClient jevApiClient;

    private ThreadPoolTaskExecutor executor;
    private InterestScoringListener listener;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        executor = new InterestScoringConfig().interestScoringExecutor(new MyfeederProperties());
        executor.initialize();
        ScoringQueue queue = new ScoringQueue(executor, scorer, store, new MyfeederProperties());
        listener = new InterestScoringListener(jevApiClient, queue);
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.shutdown();
    }

    @Test
    void ingestedEventReachesTheScorerOnTheScoringThread() throws InterruptedException {
        when(jevApiClient.isConfigured()).thenReturn(true);
        when(store.filterNeedingScoring(any(), any())).thenReturn(List.of(12L, 11L));
        List<String> threads = new CopyOnWriteArrayList<>();
        List<Long> scored = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(2);
        doAnswer(inv -> {
            threads.add(Thread.currentThread().getName());
            scored.add(inv.getArgument(0));
            done.countDown();
            return null;
        }).when(scorer).score(anyLong());

        listener.onArticlesIngested(new ArticlesIngestedEvent(1L, List.of(11L, 12L)));

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scored).containsExactly(12L, 11L);
        assertThat(threads).hasSize(2).allSatisfy(name -> assertThat(name).startsWith("jev-score-"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(store).filterNeedingScoring(ids.capture(), cutoff.capture());
        assertThat(ids.getValue()).containsExactly(11L, 12L);
        assertThat(cutoff.getValue())
                .isCloseTo(Instant.now().minus(Duration.ofDays(14)), within(5, ChronoUnit.SECONDS));
    }
}
