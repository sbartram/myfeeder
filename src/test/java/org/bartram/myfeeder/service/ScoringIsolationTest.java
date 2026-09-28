package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.InterestScoringConfig;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.event.ArticlesIngestedEvent;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private FeedRepository feedRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private FeedParser feedParser;
    @Mock private FeedFetcher feedFetcher;

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

    @Test
    void pollFinishesWhileScoringIsBlocked() throws InterruptedException {
        when(jevApiClient.isConfigured()).thenReturn(true);
        when(store.filterNeedingScoring(any(), any())).thenReturn(List.of(102L, 101L));
        CountDownLatch started = new CountDownLatch(1);
        List<String> threads = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            threads.add(Thread.currentThread().getName());
            started.countDown();
            release.await(30, TimeUnit.SECONDS); // a Jev call hanging for up to 30 s
            return null;
        }).when(scorer).score(anyLong());
        FeedPollingService polling = pollingService();

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> polling.pollFeed(1L));

        assertThat(started.await(5, TimeUnit.SECONDS)).as("scoring started").isTrue();
        assertThat(threads).allSatisfy(name -> assertThat(name).startsWith("jev-score-"));
        Feed saved = lastSavedFeed();
        assertThat(saved.getErrorCount()).isZero();
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getLastSuccessfulPollAt()).isNotNull();
    }

    @Test
    void scorerFailureNeverReachesTheFeed() throws InterruptedException {
        when(jevApiClient.isConfigured()).thenReturn(true);
        when(store.filterNeedingScoring(any(), any())).thenReturn(List.of(102L, 101L));
        CountDownLatch ran = new CountDownLatch(2);
        doAnswer(inv -> {
            ran.countDown();
            throw new IllegalStateException("scorer bug");
        }).when(scorer).score(anyLong());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> pollingService().pollFeed(1L));

        assertThat(ran.await(5, TimeUnit.SECONDS)).as("scorer ran").isTrue();
        Feed saved = lastSavedFeed();
        assertThat(saved.getErrorCount()).isZero();
        assertThat(saved.getLastError()).isNull();
    }

    @Test
    void unconfiguredJevEnqueuesNothing() {
        when(jevApiClient.isConfigured()).thenReturn(false);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> pollingService().pollFeed(1L));

        verify(jevApiClient, atLeastOnce()).isConfigured();
        verifyNoInteractions(store, scorer);
        assertThat(lastSavedFeed().getErrorCount()).isZero();
    }

    @Test
    void enqueueFailureNeverFailsThePoll() {
        when(jevApiClient.isConfigured()).thenReturn(true);
        when(store.filterNeedingScoring(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database down"));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> pollingService().pollFeed(1L));

        verify(store).filterNeedingScoring(any(), any());
        verifyNoInteractions(scorer);
        Feed saved = lastSavedFeed();
        assertThat(saved.getErrorCount()).isZero();
        assertThat(saved.getLastError()).isNull();
    }

    /**
     * The real FeedPollingService wired to the real listener, queue and executor. The publisher calls
     * the listener inline, which is how Spring invokes a fallbackExecution listener when no
     * transaction is active (pollFeed has none).
     */
    private FeedPollingService pollingService() {
        byte[] body = "<rss/>".getBytes(StandardCharsets.UTF_8);
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("Feed");
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(0);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any()))
                .thenReturn(new FetchResult(body, "application/rss+xml", null, null, false));
        when(feedParser.parse(body, "application/rss+xml")).thenReturn(ParsedFeed.builder()
                .title("Feed")
                .articles(List.of(
                        ParsedArticle.builder().guid("g-1").title("One").build(),
                        ParsedArticle.builder().guid("g-2").title("Two").build()))
                .build());
        when(articleRepository.existsByFeedIdAndGuid(anyLong(), anyString())).thenReturn(false);
        AtomicLong nextId = new AtomicLong(101L);
        when(articleRepository.save(any())).thenAnswer(inv -> {
            Article article = inv.getArgument(0);
            article.setId(nextId.getAndIncrement());
            return article;
        });
        ApplicationEventPublisher publisher = event -> listener.onArticlesIngested((ArticlesIngestedEvent) event);
        return new FeedPollingService(feedRepository, articleRepository, feedParser, feedFetcher, publisher);
    }

    private Feed lastSavedFeed() {
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
